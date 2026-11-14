package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class GradingTest extends IntegrationTestBase {
    private static int seq;

    MockHttpSession admin;
    MockHttpSession owner;
    MockHttpSession g1;
    MockHttpSession g2;
    MockHttpSession g3;
    long g1Id;
    long g2Id;
    long g3Id;
    long exam;
    String p;

    private static final String QUESTIONS = """
            {"id":"q1","type":"single","stem":"1+1=?","points":5,
             "choices":[{"id":"a","text":"1"},{"id":"b","text":"2"}],"correct":["b"]},
            {"id":"e1","type":"essay","stem":"試證明…","points":10,"rubric":"規準一"},
            {"id":"e2","type":"essay","stem":"試說明…","points":20,"rubric":"規準二"}""";

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        p = "G" + n + "-";
        admin = adminSession();
        long unit = createUnit(admin, "評分測試單位 " + n);
        Object[] o = createStaff(admin, "g-owner" + n + "@test.tw", "負責人");
        grant(admin, unit, "g-owner" + n + "@test.tw", "OWNER");
        grant(admin, unit, "g-owner" + n + "@test.tw", "AUTHOR");
        owner = login("g-owner" + n + "@test.tw", (String) o[1]);
        Object[] a = createStaff(admin, "g-a" + n + "@test.tw", "評分甲");
        Object[] b = createStaff(admin, "g-b" + n + "@test.tw", "評分乙");
        Object[] c = createStaff(admin, "g-c" + n + "@test.tw", "評分丙");
        for (String e : List.of("g-a", "g-b", "g-c")) grant(admin, unit, e + n + "@test.tw", "GRADER");
        g1 = login("g-a" + n + "@test.tw", (String) a[1]);
        g2 = login("g-b" + n + "@test.tw", (String) b[1]);
        g3 = login("g-c" + n + "@test.tw", (String) c[1]);
        g1Id = (long) a[0];
        g2Id = (long) b[0];
        g3Id = (long) c[0];

        var bank = mvc.perform(json(post("/api/banks"), "{\"unitId\":%d,\"name\":\"題庫\",\"questions\":[%s]}".formatted(unit, QUESTIONS))
                .session(owner)).andExpect(status().isCreated()).andReturn();
        var e = mvc.perform(json(post("/api/exams"), "{\"name\":\"評分測試\",\"bankId\":%s,\"startAt\":\"%s\",\"duration\":30}"
                .formatted(read(bank, "$.id").toString(), Instant.now().minusSeconds(5))).session(owner))
                .andExpect(status().isCreated()).andReturn();
        exam = ((Number) read(e, "$.id")).longValue();
        mvc.perform(json(post("/api/exams/" + exam + "/candidates"),
                "{\"rows\":[{\"ticket\":\"%s1\",\"name\":\"王小明\"},{\"ticket\":\"%s2\",\"name\":\"李宜華\"},{\"ticket\":\"%s3\",\"name\":\"缺考的人\"}]}"
                        .formatted(p, p, p)).session(owner)).andExpect(status().isCreated());
        mvc.perform(post("/api/exams/" + exam + "/publish").with(csrf()).session(owner)).andExpect(status().isOk());

        var list = mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andReturn();
        // 王小明：選擇題對、兩題申論都寫；李宜華：選擇題錯、只寫 e1（e2 空白 = 0 分，不用評）
        take(p + "1", read(list, "$[0].code"), "b", "甲的 e1 作答", "甲的 e2 作答");
        take(p + "2", read(list, "$[1].code"), "a", "乙的 e1 作答", "   ");
    }

    private void take(String ticket, String code, String choice, String e1, String e2) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code)).session(s))
                .andExpect(status().isOk());
        mvc.perform(post("/api/candidate/start").with(csrf()).session(s)).andExpect(status().isOk());
        mvc.perform(json(put("/api/candidate/answers/q1"), "{\"choices\":[\"%s\"]}".formatted(choice)).session(s)).andExpect(status().isOk());
        mvc.perform(json(put("/api/candidate/answers/e1"), "{\"essay\":\"%s\"}".formatted(e1)).session(s)).andExpect(status().isOk());
        mvc.perform(json(put("/api/candidate/answers/e2"), "{\"essay\":\"%s\"}".formatted(e2)).session(s)).andExpect(status().isOk());
        mvc.perform(post("/api/candidate/submit").with(csrf()).session(s)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions candidateLogin(String ticket, String code) throws Exception {
        return mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"%s\",\"code\":\"%s\"}".formatted(ticket, code))
                .session(new MockHttpSession())).andExpect(status().isOk());
    }

    private void assign(long... ids) throws Exception {
        String list = java.util.Arrays.stream(ids).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        mvc.perform(json(put("/api/exams/" + exam + "/graders"), "{\"staffIds\":[" + list + "]}").session(owner))
                .andExpect(status().isOk());
    }

    /** 以某位評分者的身分，依答案內容找到該項並給分。 */
    private void score(MockHttpSession g, String answer, String score, int expected) throws Exception {
        var items = mvc.perform(get("/api/grading/exams/" + exam + "/items").session(g)).andExpect(status().isOk()).andReturn();
        List<String> anon = read(items, "$[?(@.answer=='" + answer + "')].anonId");
        List<String> q = read(items, "$[?(@.answer=='" + answer + "')].questionId");
        mvc.perform(json(put("/api/grading/exams/" + exam + "/items/" + anon.get(0) + "/" + q.get(0)),
                "{\"score\":" + score + ",\"comment\":\"ok\"}").session(g)).andExpect(status().is(expected));
    }

    @Test
    void 多位評分者_匿名_獨立_平均_差距標記_公布() throws Exception {
        // 還沒指派：評分者看不到；承辦人看到未完成
        mvc.perform(get("/api/grading/exams").session(g1)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/grading/exams/" + exam + "/items").session(g1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/" + exam + "/results").session(owner)).andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.itemsTotal").value(3)).andExpect(jsonPath("$.fullMarks").value(35.0));

        assign(g1Id, g2Id);
        mvc.perform(get("/api/exams/" + exam + "/graders").session(owner)).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.assigned==true)].length()").value(org.hamcrest.Matchers.hasSize(2)));
        mvc.perform(get("/api/grading/exams").session(g1)).andExpect(jsonPath("$[0].total").value(3))
                .andExpect(jsonPath("$[0].done").value(0));
        mvc.perform(get("/api/grading/exams/" + exam + "/items").session(g3)).andExpect(status().isNotFound());

        // 匿名：看不到姓名、准考證號；看得到評分規準
        var items = mvc.perform(get("/api/grading/exams/" + exam + "/items").session(g1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].rubric").exists()).andReturn();
        assertThat(items.getResponse().getContentAsString()).doesNotContain("王小明", "李宜華", p + "1", p + "2");

        score(g1, "甲的 e1 作答", "8", 204);
        score(g1, "甲的 e1 作答", "9", 409);     // 送出後不能自己改
        score(g1, "甲的 e2 作答", "25", 400);    // 超過滿分
        score(g1, "甲的 e2 作答", "7.3", 400);   // 要以 0.5 為單位
        score(g1, "甲的 e2 作答", "18", 204);
        score(g1, "乙的 e1 作答", "3", 204);

        // 乙看不到甲給的分數
        var forG2 = mvc.perform(get("/api/grading/exams/" + exam + "/items").session(g2))
                .andExpect(jsonPath("$[?(@.myScore!=null)]").isEmpty()).andReturn();
        assertThat(forG2.getResponse().getContentAsString()).doesNotContain("\"myScore\":8");
        // 還沒鎖定：承辦人看得到進度，看不到分數
        mvc.perform(get("/api/exams/" + exam + "/results").session(owner)).andExpect(jsonPath("$.itemsLocked").value(0))
                .andExpect(jsonPath("$.items[0].graded").value(1)).andExpect(jsonPath("$.items[0].scores.length()").value(0))
                .andExpect(jsonPath("$.rows[0].total").isEmpty()).andExpect(jsonPath("$.rows[0].pending").value(2));
        mvc.perform(get("/api/exams/" + exam + "/graders").session(owner))
                .andExpect(jsonPath("$[?(@.staffId==" + g1Id + ")].done").value(org.hamcrest.Matchers.contains(3)));

        score(g2, "甲的 e1 作答", "9", 204);     // 平均 8.5
        score(g2, "甲的 e2 作答", "10", 204);    // 18 與 10 差 8 > 20×0.2 → 標記；平均 14
        score(g2, "乙的 e1 作答", "4.5", 204);   // 平均 3.75 → 3.8

        var r = mvc.perform(get("/api/exams/" + exam + "/results").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsLocked").value(3))
                // 被標記的排最前面
                .andExpect(jsonPath("$.items[0].flagged").value(true)).andExpect(jsonPath("$.items[0].average").value(14.0))
                .andExpect(jsonPath("$.items[0].scores[0]").value(10.0)).andExpect(jsonPath("$.items[1].flagged").value(false))
                // 王小明 5 + 8.5 + 14 = 27.5；李宜華 0 + 3.8 = 3.8；缺考沒有分數
                .andExpect(jsonPath("$.rows[0].autoScore").value(5.0)).andExpect(jsonPath("$.rows[0].essayScore").value(22.5))
                .andExpect(jsonPath("$.rows[0].total").value(27.5)).andExpect(jsonPath("$.rows[1].total").value(3.8))
                .andExpect(jsonPath("$.rows[2].status").value("IDLE")).andExpect(jsonPath("$.rows[2].total").isEmpty()).andReturn();
        String flaggedAnon = read(r, "$.items[0].anonId");

        // 考試還沒結束不能公布
        mvc.perform(post("/api/exams/" + exam + "/results/publish").with(csrf()).session(owner)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("not_ended"));
        mvc.perform(post("/api/exams/" + exam + "/end").with(csrf()).session(owner)).andExpect(status().isOk());

        // 重開被標記的那一題：要填理由；重開後變回未完成，評分者可以重評
        mvc.perform(json(post("/api/exams/" + exam + "/grading/reopen"),
                "{\"anonId\":\"%s\",\"questionId\":\"e2\",\"reason\":\"\"}".formatted(flaggedAnon)).session(owner))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/exams/" + exam + "/grading/reopen"),
                "{\"anonId\":\"%s\",\"questionId\":\"e2\",\"reason\":\"兩位差距過大\"}".formatted(flaggedAnon)).session(owner))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/exams/" + exam + "/results/publish").with(csrf()).session(owner)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("incomplete"));
        score(g1, "甲的 e2 作答", "16", 204);
        score(g2, "甲的 e2 作答", "14", 204);
        mvc.perform(get("/api/admin/audit-logs").param("action", "GRADING_REOPENED").session(admin))
                .andExpect(jsonPath("$.items[0].reason").value("兩位差距過大"))
                .andExpect(jsonPath("$.items[0].detail").value("e2 原分數 18.0、10.0"));

        // 已經評過分的評分者不能移除
        mvc.perform(json(put("/api/exams/" + exam + "/graders"), "{\"staffIds\":[" + g1Id + "]}").session(owner))
                .andExpect(status().isConflict());

        // 公布前：考生登入看不到成績
        var codes = mvc.perform(get("/api/exams/" + exam + "/candidates").session(owner)).andReturn();
        candidateLogin(p + "1", read(codes, "$[0].code")).andExpect(jsonPath("$.attempt.submittedAt").isNotEmpty())
                .andExpect(jsonPath("$.result").isEmpty());

        mvc.perform(post("/api/exams/" + exam + "/results/publish").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedAt").isNotEmpty()).andExpect(jsonPath("$.rows[0].total").value(28.5));
        // 公布後：考生只看得到自己的分數；缺考的人沒有分數
        candidateLogin(p + "1", read(codes, "$[0].code")).andExpect(jsonPath("$.result.total").value(28.5))
                .andExpect(jsonPath("$.result.autoScore").value(5.0)).andExpect(jsonPath("$.result.essayScore").value(23.5))
                .andExpect(jsonPath("$.result.fullMarks").value(35.0)).andExpect(jsonPath("$.result.publishedAt").isNotEmpty());
        candidateLogin(p + "3", read(codes, "$[2].code")).andExpect(jsonPath("$.attempt").isEmpty())
                .andExpect(jsonPath("$.result.total").isEmpty()).andExpect(jsonPath("$.result.fullMarks").value(35.0));
        // 公布後：評分關閉、不能重開
        mvc.perform(get("/api/grading/exams/" + exam + "/items").session(g1)).andExpect(status().isConflict());
        mvc.perform(json(post("/api/exams/" + exam + "/grading/reopen"),
                "{\"anonId\":\"%s\",\"questionId\":\"e2\",\"reason\":\"x\"}".formatted(flaggedAnon)).session(owner))
                .andExpect(status().isConflict());

        var csvResult = mvc.perform(get("/api/exams/" + exam + "/results.csv").session(owner)).andExpect(status().isOk()).andReturn();
        String csvText = new String(csvResult.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvText).contains("座位,准考證號,姓名", p + "1,王小明,,已完成,5,23.5,28.5", "缺考的人,,缺考,,,");

        var xlsx = mvc.perform(get("/api/exams/" + exam + "/results.xlsx").session(owner)).andExpect(status().isOk()).andReturn();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("准考證號");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("王小明");
            assertThat(sheet.getRow(1).getCell(7).getNumericCellValue()).isEqualTo(28.5);
            assertThat(sheet.getRow(3).getCell(4).getStringCellValue()).isEqualTo("缺考");
            assertThat(sheet.getRow(3).getCell(7)).isNull();
        }
    }

    @Test
    void 只有一位評分者也可以_分數就是他給的() throws Exception {
        assign(g3Id);
        score(g3, "甲的 e1 作答", "7", 204);
        score(g3, "甲的 e2 作答", "12.5", 204);
        score(g3, "乙的 e1 作答", "10", 204);
        mvc.perform(get("/api/exams/" + exam + "/results").session(owner)).andExpect(jsonPath("$.itemsLocked").value(3))
                .andExpect(jsonPath("$.graders").value(1)).andExpect(jsonPath("$.items[0].flagged").value(false))
                .andExpect(jsonPath("$.rows[0].total").value(24.5)).andExpect(jsonPath("$.rows[1].total").value(10.0));
    }

    @Test
    void 權限() throws Exception {
        assign(g1Id);
        // 評分者不能看成績、不能指派；承辦人不是評分者不能評分；系統管理員都不行
        mvc.perform(get("/api/exams/" + exam + "/results").session(g1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/" + exam + "/results.csv").session(g1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/" + exam + "/results.xlsx").session(g1)).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/exams/" + exam + "/graders"), "{\"staffIds\":[]}").session(g1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/grading/exams/" + exam + "/items").session(owner)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exams/" + exam + "/results").session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/grading/exams/" + exam + "/items").session(admin)).andExpect(status().isNotFound());
        // 不能指派沒有評分角色的人（負責人自己）
        var me = mvc.perform(get("/api/auth/me").session(owner)).andReturn();
        mvc.perform(json(put("/api/exams/" + exam + "/graders"), "{\"staffIds\":[" + read(me, "$.id") + "]}").session(owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("not_grader"));
    }
}
