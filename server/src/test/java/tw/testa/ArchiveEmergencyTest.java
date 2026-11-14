package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

/** 封存與保存期限、系統管理員的緊急檢視（學校 2026-11-14 的決定）。 */
class ArchiveEmergencyTest extends IntegrationTestBase {
    private static int seq;

    @Autowired
    JdbcTemplate jdbc;

    MockHttpSession admin;
    MockHttpSession owner;
    MockHttpSession proctor;
    long adminId;
    long exam;
    String ticket;
    String code;

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        ticket = "R" + n + "-1";
        admin = adminSession();
        adminId = ((Number) read(mvc.perform(get("/api/auth/me").session(admin)).andReturn(), "$.id")).longValue();
        long unit = createUnit(admin, "封存測試單位 " + n);
        Object[] o = createStaff(admin, "r-owner" + n + "@test.tw", "負責人");
        grant(admin, unit, "r-owner" + n + "@test.tw", "OWNER");
        grant(admin, unit, "r-owner" + n + "@test.tw", "AUTHOR");
        owner = login("r-owner" + n + "@test.tw", (String) o[1]);
        Object[] pr = createStaff(admin, "r-proctor" + n + "@test.tw", "監考");
        grant(admin, unit, "r-proctor" + n + "@test.tw", "PROCTOR");
        proctor = login("r-proctor" + n + "@test.tw", (String) pr[1]);

        var bank = mvc.perform(json(post("/api/banks"), """
                {"unitId":%d,"name":"題庫","questions":[{"id":"q1","type":"single","stem":"1+1=?","points":5,
                 "choices":[{"id":"a","text":"1"},{"id":"b","text":"2"}],"correct":["b"]}]}""".formatted(unit)).session(owner))
                .andExpect(status().isCreated()).andReturn();
        var e = mvc.perform(json(post("/api/exams"), "{\"name\":\"封存測試\",\"bankId\":%s,\"startAt\":\"%s\",\"duration\":30}"
                .formatted(read(bank, "$.id").toString(), Instant.now().minusSeconds(5))).session(owner))
                .andExpect(status().isCreated()).andReturn();
        exam = ((Number) read(e, "$.id")).longValue();
        mvc.perform(json(post("/api/exams/" + exam + "/candidates"),
                "{\"rows\":[{\"ticket\":\"%s\",\"name\":\"王小明\"}]}".formatted(ticket)).session(owner))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/exams/" + exam + "/publish").with(csrf()).session(owner)).andExpect(status().isOk());
        code = read(mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andReturn(), "$[0].code");

        MockHttpSession s = new MockHttpSession();
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code)).session(s))
                .andExpect(status().isOk());
        mvc.perform(post("/api/candidate/start").with(csrf()).session(s)).andExpect(status().isOk());
        mvc.perform(json(put("/api/candidate/answers/q1"), "{\"choices\":[\"b\"]}").session(s)).andExpect(status().isOk());
        mvc.perform(post("/api/candidate/submit").with(csrf()).session(s)).andExpect(status().isOk());
    }

    private void end() throws Exception {
        mvc.perform(post("/api/exams/" + exam + "/end").with(csrf()).session(owner)).andExpect(status().isOk());
    }

    @Test
    void 封存後唯讀_保存期限內不能刪_到期後填理由才能刪() throws Exception {
        // 還沒結束不能封存
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(owner)).andExpect(status().isConflict());
        end();
        // 沒封存不能刪；監考不能封存
        mvc.perform(delete("/api/exams/" + exam).with(csrf()).session(owner)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("not_archived"));
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(proctor)).andExpect(status().isForbidden());

        int year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).getYear();
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                // 保留到隔年年底 = 後年 1/1 00:00（台北）= 前一天 16:00Z
                .andExpect(jsonPath("$.retainUntil").value((year + 1) + "-12-31T16:00:00Z"));

        // 封存後：考生不能登入、不能公布成績、不能改評分者
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code))
                .session(new MockHttpSession())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/exams/" + exam + "/results/publish").with(csrf()).session(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("archived"));
        mvc.perform(json(put("/api/exams/" + exam + "/graders"), "{\"staffIds\":[]}").session(owner))
                .andExpect(status().isConflict());
        // 成績表還是看得到
        mvc.perform(get("/api/exams/" + exam + "/results").session(owner)).andExpect(status().isOk());

        // 保存期限內不能刪
        mvc.perform(json(delete("/api/exams/" + exam), "{\"reason\":\"到期\"}").session(owner)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("retained"));

        // 取消封存後考生又能登入；再封存
        mvc.perform(post("/api/exams/" + exam + "/unarchive").with(csrf()).session(owner)).andExpect(status().isOk());
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code))
                .session(new MockHttpSession())).andExpect(status().isOk());
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(owner)).andExpect(status().isOk());

        // 把結束時間改成三年前，模擬過了保存期限
        jdbc.update("update exam set ended_at = now() - interval '3 years' where id = ?", exam);
        mvc.perform(delete("/api/exams/" + exam).with(csrf()).session(owner)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("no_reason"));
        mvc.perform(json(delete("/api/exams/" + exam), "{\"reason\":\"保存期限屆滿\"}").session(proctor))
                .andExpect(status().isForbidden());
        mvc.perform(json(delete("/api/exams/" + exam), "{\"reason\":\"保存期限屆滿\"}").session(owner))
                .andExpect(status().isNoContent());

        // 考生、答卷都不在了；操作紀錄留著
        assertThat(jdbc.queryForObject("select count(*) from candidate where exam_id = ?", Long.class, exam)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attempt where exam_id = ?", Long.class, exam)).isZero();
        assertThat(jdbc.queryForObject(
                "select reason from audit_log where action = 'EXAM_PURGED' and target_id = ?", String.class,
                String.valueOf(exam))).isEqualTo("保存期限屆滿");
    }

    @Test
    void 緊急檢視_要負責人授權_只能看_有紀錄_可撤銷() throws Exception {
        end();
        // 沒授權：系統管理員什麼都看不到
        mvc.perform(get("/api/exams/" + exam).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/emergency-access").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 只有負責人能授權；要填理由；對象必須是系統管理員
        String body = "{\"staffId\":%d,\"reason\":\"考生申訴成績，需要協助查證\",\"hours\":2}".formatted(adminId);
        mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"), body).session(proctor)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"), body).session(admin)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"),
                "{\"staffId\":%d,\"reason\":\" \",\"hours\":2}".formatted(adminId)).session(owner)).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"),
                "{\"staffId\":%d,\"reason\":\"x\",\"hours\":200}".formatted(adminId)).session(owner)).andExpect(status().isBadRequest());
        long ownerId = ((Number) read(mvc.perform(get("/api/auth/me").session(owner)).andReturn(), "$.id")).longValue();
        mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"),
                "{\"staffId\":%d,\"reason\":\"x\",\"hours\":2}".formatted(ownerId)).session(owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("not_admin"));

        var g = mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"), body).session(owner))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true)).andReturn();
        long grantId = ((Number) read(g, "$.id")).longValue();
        mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"), body).session(owner)).andExpect(status().isConflict());

        // 系統管理員看得到這筆授權，也看得到考試、名單、成績；但沒有登入碼
        mvc.perform(get("/api/admin/emergency-access").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].examName").value("封存測試"));
        var view = mvc.perform(get("/api/admin/emergency-access/" + grantId).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exam.name").value("封存測試"))
                .andExpect(jsonPath("$.candidates[0].name").value("王小明"))
                .andExpect(jsonPath("$.candidates[0].code").doesNotExist())
                .andExpect(jsonPath("$.results.rows[0].total").value(5.0)).andReturn();
        assertThat(view.getResponse().getContentAsString()).doesNotContain(code);

        // 授權不會打開其他入口：平常的 API 還是看不到，也不能改
        mvc.perform(get("/api/exams/" + exam).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/" + exam + "/results").session(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(admin)).andExpect(status().isNotFound());
        // 別人拿這個授權編號也看不到
        mvc.perform(get("/api/admin/emergency-access/" + grantId).session(owner)).andExpect(status().isForbidden());

        // 負責人看得到被看了幾次；授權、檢視都有操作紀錄
        mvc.perform(get("/api/exams/" + exam + "/emergency-access").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.grants[0].viewCount").value(1)).andExpect(jsonPath("$.admins[0].staffId").isNumber());
        for (String action : new String[] {"EMERGENCY_ACCESS_GRANTED", "EMERGENCY_ACCESS_VIEWED"}) {
            assertThat(jdbc.queryForObject("select count(*) from audit_log where action = ? and target_id = ?", Long.class,
                    action, String.valueOf(exam))).isEqualTo(1L);
        }

        // 撤銷後立刻失效
        mvc.perform(delete("/api/exams/" + exam + "/emergency-access/" + grantId).with(csrf()).session(owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/emergency-access/" + grantId).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/emergency-access").session(admin)).andExpect(jsonPath("$.length()").value(0));

        // 過期的也一樣
        var g2 = mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"), body).session(owner))
                .andExpect(status().isCreated()).andReturn();
        long grant2 = ((Number) read(g2, "$.id")).longValue();
        jdbc.update("update exam_emergency_access set expires_at = now() - interval '1 minute' where id = ?", grant2);
        mvc.perform(get("/api/admin/emergency-access/" + grant2).session(admin)).andExpect(status().isNotFound());
    }
}
