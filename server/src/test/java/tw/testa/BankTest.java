package tw.testa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class BankTest extends IntegrationTestBase {
    private static int seq;

    long unit;
    long otherUnit;
    MockHttpSession author;
    MockHttpSession owner;
    MockHttpSession grader;
    MockHttpSession outsider;
    MockHttpSession admin;

    private static final String Q1 = """
            {"id":"q_1","type":"single","stem":"若 $f(x)=x^2$，則 $f(2)$ 為何？","points":5,"explanation":"代入即可",
             "choices":[{"id":"c_a","text":"2"},{"id":"c_b","text":"4"}],"correct":["c_b"],
             "blanks":[{"id":"b_1","answers":[]}],"caseSensitive":false,"rubric":"","wordLimit":null,"aiGrading":false}""";
    private static final String Q2 = """
            {"id":"q_2","type":"blank","stem":"$a_{10}=$ ＿＿","points":6.5,"explanation":"",
             "choices":[],"correct":[],"blanks":[{"id":"b_1","answers":["39","三十九"]}],
             "caseSensitive":true,"rubric":"","wordLimit":null,"aiGrading":false}""";
    private static final String Q3 = """
            {"id":"q_3","type":"essay","stem":"試證明…","points":10,"explanation":"","choices":[],"correct":[],
             "blanks":[],"caseSensitive":false,"rubric":"驗證 n=1（2 分）","wordLimit":500,"aiGrading":false,"review":true}""";

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        admin = adminSession();
        unit = createUnit(admin, "題庫測試單位 " + n);
        otherUnit = createUnit(admin, "別的單位 " + n);
        author = staffWith("author" + n + "@test.tw", unit, "AUTHOR");
        owner = staffWith("owner" + n + "@test.tw", unit, "OWNER");
        grader = staffWith("grader" + n + "@test.tw", unit, "GRADER");
        outsider = staffWith("outsider" + n + "@test.tw", otherUnit, "AUTHOR");
    }

    private MockHttpSession staffWith(String email, long unitId, String role) throws Exception {
        Object[] s = createStaff(admin, email, email);
        grant(admin, unitId, email, role);
        return login(email, (String) s[1]);
    }

    private long createBank(MockHttpSession by) throws Exception {
        var r = mvc.perform(json(post("/api/banks"), "{\"unitId\":%d}".formatted(unit)).session(by))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("未命名題庫"))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.editable").value(true)).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    private static String body(int version, String name, String... questions) {
        return "{\"version\":%d,\"name\":\"%s\",\"subject\":\"數學\",\"duration\":80,\"questions\":[%s]}"
                .formatted(version, name, String.join(",", questions));
    }

    @Test
    void 出題者可以建立_儲存_讀回題庫() throws Exception {
        long id = createBank(author);

        mvc.perform(json(put("/api/banks/" + id), body(0, "入學測驗", Q1, Q2, Q3)).session(author))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.questions.length()").value(3));

        mvc.perform(get("/api/banks/" + id).session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("入學測驗")).andExpect(jsonPath("$.duration").value(80))
                .andExpect(jsonPath("$.questions[0].id").value("q_1"))
                .andExpect(jsonPath("$.questions[0].stem").value("若 $f(x)=x^2$，則 $f(2)$ 為何？"))
                .andExpect(jsonPath("$.questions[0].correct[0]").value("c_b"))
                .andExpect(jsonPath("$.questions[0].choices[1].text").value("4"))
                .andExpect(jsonPath("$.questions[1].points").value(6.5))
                .andExpect(jsonPath("$.questions[1].blanks[0].answers[1]").value("三十九"))
                .andExpect(jsonPath("$.questions[1].caseSensitive").value(true))
                .andExpect(jsonPath("$.questions[2].wordLimit").value(500))
                .andExpect(jsonPath("$.questions[2].review").value(true))
                .andExpect(jsonPath("$.questions[2].rubric").value("驗證 n=1（2 分）"));

        // 重新排序、刪掉一題、修改一題
        String q1Edited = Q1.replace("\"points\":5", "\"points\":8");
        mvc.perform(json(put("/api/banks/" + id), body(1, "入學測驗", Q3, q1Edited)).session(author))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/banks").session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].questions.length()").value(2))
                .andExpect(jsonPath("$[0].questions[0].id").value("q_3"))
                .andExpect(jsonPath("$[0].questions[1].id").value("q_1"))
                .andExpect(jsonPath("$[0].questions[1].points").value(8.0));
    }

    @Test
    void 版本不符時拒絕儲存_不會蓋掉別人的修改() throws Exception {
        long id = createBank(author);
        mvc.perform(json(put("/api/banks/" + id), body(0, "第一個人存的", Q1)).session(author))
                .andExpect(status().isOk());
        // 第二個分頁還拿著舊版本
        mvc.perform(json(put("/api/banks/" + id), body(0, "第二個人存的")).session(author))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("stale_version"));
        mvc.perform(get("/api/banks/" + id).session(author)).andExpect(jsonPath("$.name").value("第一個人存的"))
                .andExpect(jsonPath("$.questions.length()").value(1));
    }

    @Test
    void 權限_負責人唯讀_其他人看不到() throws Exception {
        long id = createBank(author);

        // 負責人：看得到、不能改
        mvc.perform(get("/api/banks").session(owner)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].editable").value(false));
        mvc.perform(get("/api/banks/" + id).session(owner)).andExpect(status().isOk());
        mvc.perform(json(put("/api/banks/" + id), body(0, "負責人想改")).session(owner))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/banks"), "{\"unitId\":%d}".formatted(unit)).session(owner))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/banks/" + id).with(csrf()).session(owner)).andExpect(status().isForbidden());

        // 評分者、別的單位的出題者、系統管理員：看不到
        for (MockHttpSession s : new MockHttpSession[] { grader, outsider, admin }) {
            mvc.perform(get("/api/banks").session(s)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id==" + id + ")]").isEmpty());
            mvc.perform(get("/api/banks/" + id).session(s)).andExpect(status().isNotFound());
            mvc.perform(json(put("/api/banks/" + id), body(0, "偷改")).session(s)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/banks/" + id).with(csrf()).session(s)).andExpect(status().isNotFound());
        }
        // 別的單位的出題者不能把題庫建到這個單位
        mvc.perform(json(post("/api/banks"), "{\"unitId\":%d}".formatted(unit)).session(outsider))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/banks")).andExpect(status().isUnauthorized());
    }

    @Test
    void 刪除題庫() throws Exception {
        long id = createBank(author);
        mvc.perform(json(put("/api/banks/" + id), body(0, "要刪的", Q1, Q2)).session(author)).andExpect(status().isOk());
        mvc.perform(delete("/api/banks/" + id).with(csrf()).session(author)).andExpect(status().isNoContent());
        mvc.perform(get("/api/banks/" + id).session(author)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/audit-logs").param("action", "BANK_DELETED").session(admin))
                .andExpect(jsonPath("$.items[0].detail").value("要刪的"));
    }

    @Test
    void 資料矛盾時拒絕() throws Exception {
        long id = createBank(author);
        mvc.perform(json(put("/api/banks/" + id), body(0, "x", Q1, Q1)).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("duplicate_question"));
        mvc.perform(json(put("/api/banks/" + id), body(0, "x", Q1.replace("[\"c_b\"]", "[\"c_zzz\"]"))).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unknown_choice"));
        mvc.perform(json(put("/api/banks/" + id), body(0, "x", Q1.replace("\"points\":5", "\"points\":500")))
                .session(author)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        mvc.perform(json(put("/api/banks/" + id), body(0, "x", Q1.replace("\"single\"", "\"magic\""))).session(author))
                .andExpect(status().isBadRequest());
        // 沒寫完的題目是允許的
        String draft = "{\"id\":\"q_d\",\"type\":\"single\",\"points\":5}";
        mvc.perform(json(put("/api/banks/" + id), body(0, "x", draft)).session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].stem").value(""))
                .andExpect(jsonPath("$.questions[0].choices.length()").value(0));
    }
}
