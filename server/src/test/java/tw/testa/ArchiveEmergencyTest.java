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

/** Archiving, retention and emergency access for administrators (school decisions of 2026-11-14). */
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
        code = read(issueCodes(owner, exam), "$[0].code");

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
    void archivedIsReadOnly_cannotDeleteWithinRetention_canDeleteWithReasonAfter() throws Exception {
        // Cannot archive before the exam ends
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(owner)).andExpect(status().isConflict());
        end();
        // Cannot delete without archiving; proctors cannot archive
        mvc.perform(delete("/api/exams/" + exam).with(csrf()).session(owner)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("not_archived"));
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(proctor)).andExpect(status().isForbidden());

        int year = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).getYear();
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty())
                // Kept until the end of next year = 1 Jan the year after, 00:00 Taipei = 16:00Z the day before
                .andExpect(jsonPath("$.retainUntil").value((year + 1) + "-12-31T16:00:00Z"));

        // Archived: candidates cannot sign in, results cannot be published, graders cannot change
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code))
                .session(new MockHttpSession())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/exams/" + exam + "/results/publish").with(csrf()).session(owner))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("archived"));
        mvc.perform(json(put("/api/exams/" + exam + "/graders"), "{\"staffIds\":[]}").session(owner))
                .andExpect(status().isConflict());
        // The score sheet is still readable
        mvc.perform(get("/api/exams/" + exam + "/results").session(owner)).andExpect(status().isOk());

        // Cannot delete within retention
        mvc.perform(json(delete("/api/exams/" + exam), "{\"reason\":\"到期\"}").session(owner)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("retained"));

        // Unarchived: candidates can sign in again; archive again
        mvc.perform(post("/api/exams/" + exam + "/unarchive").with(csrf()).session(owner)).andExpect(status().isOk());
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code))
                .session(new MockHttpSession())).andExpect(status().isOk());
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(owner)).andExpect(status().isOk());

        // Move the end time three years back to pass retention
        jdbc.update("update exam set ended_at = now() - interval '3 years' where id = ?", exam);
        mvc.perform(delete("/api/exams/" + exam).with(csrf()).session(owner)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("no_reason"));
        mvc.perform(json(delete("/api/exams/" + exam), "{\"reason\":\"保存期限屆滿\"}").session(proctor))
                .andExpect(status().isForbidden());
        mvc.perform(json(delete("/api/exams/" + exam), "{\"reason\":\"保存期限屆滿\"}").session(owner))
                .andExpect(status().isNoContent());

        // Candidates and papers are gone; the audit log stays
        assertThat(jdbc.queryForObject("select count(*) from candidate where exam_id = ?", Long.class, exam)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from attempt where exam_id = ?", Long.class, exam)).isZero();
        assertThat(jdbc.queryForObject(
                "select reason from audit_log where action = 'EXAM_PURGED' and target_id = ?", String.class,
                String.valueOf(exam))).isEqualTo("保存期限屆滿");
    }

    @Test
    void emergencyAccess_needsOwnerGrant_readOnly_audited_revocable() throws Exception {
        end();
        // Without a grant the administrator sees nothing
        mvc.perform(get("/api/exams/" + exam).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/emergency-access").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Only owners grant; a reason is required; the grantee must be a system administrator
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

        // The administrator sees the grant, the exam, roster and results, but no login codes
        mvc.perform(get("/api/admin/emergency-access").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].examName").value("封存測試"));
        var view = mvc.perform(get("/api/admin/emergency-access/" + grantId).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.exam.name").value("封存測試"))
                .andExpect(jsonPath("$.candidates[0].name").value("王小明"))
                .andExpect(jsonPath("$.candidates[0].code").doesNotExist())
                .andExpect(jsonPath("$.results.rows[0].total").value(5.0)).andReturn();
        assertThat(view.getResponse().getContentAsString()).doesNotContain(code);

        // A grant opens no other door: the regular API still hides everything and nothing can be changed
        mvc.perform(get("/api/exams/" + exam).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/" + exam + "/results").session(admin)).andExpect(status().isNotFound());
        mvc.perform(post("/api/exams/" + exam + "/archive").with(csrf()).session(admin)).andExpect(status().isNotFound());
        // Nobody else can use this grant id
        mvc.perform(get("/api/admin/emergency-access/" + grantId).session(owner)).andExpect(status().isForbidden());

        // The owner sees the view count; grants and views are audited
        mvc.perform(get("/api/exams/" + exam + "/emergency-access").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.grants[0].viewCount").value(1)).andExpect(jsonPath("$.admins[0].staffId").isNumber());
        for (String action : new String[] {"EMERGENCY_ACCESS_GRANTED", "EMERGENCY_ACCESS_VIEWED"}) {
            assertThat(jdbc.queryForObject("select count(*) from audit_log where action = ? and target_id = ?", Long.class,
                    action, String.valueOf(exam))).isEqualTo(1L);
        }

        // Revocation takes effect at once
        mvc.perform(delete("/api/exams/" + exam + "/emergency-access/" + grantId).with(csrf()).session(owner))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/emergency-access/" + grantId).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/emergency-access").session(admin)).andExpect(jsonPath("$.length()").value(0));

        // So does expiry
        var g2 = mvc.perform(json(post("/api/exams/" + exam + "/emergency-access"), body).session(owner))
                .andExpect(status().isCreated()).andReturn();
        long grant2 = ((Number) read(g2, "$.id")).longValue();
        jdbc.update("update exam_emergency_access set expires_at = now() - interval '1 minute' where id = ?", grant2);
        mvc.perform(get("/api/admin/emergency-access/" + grant2).session(admin)).andExpect(status().isNotFound());
    }
}
