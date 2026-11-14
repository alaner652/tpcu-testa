package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

class ExamTest extends IntegrationTestBase {
    private static int seq;

    long unit;
    long bank;
    MockHttpSession admin;
    MockHttpSession owner;
    MockHttpSession proctor;
    MockHttpSession author;

    private static final String Q = """
            {"id":"q_1","type":"single","stem":"1+1=?","points":5,
             "choices":[{"id":"a","text":"1"},{"id":"b","text":"2"}],"correct":["b"]}""";

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        admin = adminSession();
        unit = createUnit(admin, "考試測試單位 " + n);
        owner = staffWith("exam-owner" + n + "@test.tw", "OWNER");
        proctor = staffWith("exam-proctor" + n + "@test.tw", "PROCTOR");
        author = staffWith("exam-author" + n + "@test.tw", "AUTHOR");
        var r = mvc.perform(json(post("/api/banks"), "{\"unitId\":%d,\"name\":\"入學題庫\"}".formatted(unit)).session(author))
                .andExpect(status().isCreated()).andReturn();
        bank = ((Number) read(r, "$.id")).longValue();
        saveBank(0, Q);
    }

    private void saveBank(int version, String... questions) throws Exception {
        mvc.perform(json(put("/api/banks/" + bank),
                "{\"version\":%d,\"name\":\"入學題庫\",\"subject\":\"數學\",\"duration\":60,\"questions\":[%s]}"
                        .formatted(version, String.join(",", questions))).session(author))
                .andExpect(status().isOk());
    }

    private MockHttpSession staffWith(String email, String role) throws Exception {
        Object[] s = createStaff(admin, email, email);
        grant(admin, unit, email, role);
        return login(email, (String) s[1]);
    }

    private long createExam(Instant startAt) throws Exception {
        String start = startAt == null ? "null" : "\"" + startAt + "\"";
        var r = mvc.perform(json(post("/api/exams"),
                "{\"name\":\"115 入學考\",\"bankId\":%d,\"startAt\":%s,\"duration\":80,\"mode\":\"CLASSROOM\"}"
                        .formatted(bank, start)).session(owner))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("draft")).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    private MockMultipartFile csv(String content, Charset charset) {
        return new MockMultipartFile("file", "名單.csv", "text/csv", content.getBytes(charset));
    }

    @Test
    void lifecycle_draft_publish_unpublish_live_ended() throws Exception {
        long id = createExam(null);

        // Cannot publish without a start time
        mvc.perform(post("/api/exams/" + id + "/publish").with(csrf()).session(owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("no_start"));

        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        mvc.perform(json(patch("/api/exams/" + id), "{\"startAt\":\"%s\",\"name\":\"改名後\"}".formatted(future)).session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("改名後"));
        mvc.perform(post("/api/exams/" + id + "/publish").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("scheduled")).andExpect(jsonPath("$.questionCount").value(1))
                .andExpect(jsonPath("$.totalPoints").value(5.0));

        // Once published: no edits, no deleting, cannot end before it starts
        mvc.perform(json(patch("/api/exams/" + id), "{\"name\":\"偷改\"}").session(owner)).andExpect(status().isConflict());
        mvc.perform(delete("/api/exams/" + id).with(csrf()).session(owner)).andExpect(status().isConflict());
        mvc.perform(post("/api/exams/" + id + "/end").with(csrf()).session(owner)).andExpect(status().isConflict());

        // Editing the bank after publishing does not affect the exam (snapshot)
        saveBank(1, Q, Q.replace("q_1", "q_2"));
        mvc.perform(get("/api/exams/" + id).session(owner)).andExpect(jsonPath("$.questionCount").value(1));

        // Unpublish, move the start into the past, publish again: live, with a new snapshot
        mvc.perform(post("/api/exams/" + id + "/withdraw").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"));
        mvc.perform(json(patch("/api/exams/" + id), "{\"startAt\":\"%s\"}".formatted(Instant.now().minusSeconds(60)))
                .session(owner)).andExpect(status().isOk());
        mvc.perform(post("/api/exams/" + id + "/publish").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("live")).andExpect(jsonPath("$.questionCount").value(2));
        mvc.perform(post("/api/exams/" + id + "/withdraw").with(csrf()).session(owner)).andExpect(status().isConflict());

        mvc.perform(post("/api/exams/" + id + "/end").with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ended"));
    }

    @Test
    void cannotPublishUnfinishedQuestions() throws Exception {
        saveBank(1, Q, "{\"id\":\"q_draft\",\"type\":\"single\",\"stem\":\"還沒選答案\",\"points\":5,"
                + "\"choices\":[{\"id\":\"a\",\"text\":\"1\"},{\"id\":\"b\",\"text\":\"2\"}],\"correct\":[]}");
        long id = createExam(Instant.now().plus(1, ChronoUnit.DAYS));
        mvc.perform(post("/api/exams/" + id + "/publish").with(csrf()).session(owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("incomplete_question"))
                .andExpect(jsonPath("$.detail").value("第 2 題還沒有設定正確答案，請出題者補完後再發布。"));
    }

    @Test
    void permissions_ownerManages_proctorReadsAndResetsCodes_othersSeeNothing() throws Exception {
        long id = createExam(null);
        String create = "{\"name\":\"x\",\"bankId\":%d,\"duration\":60}".formatted(bank);

        // Proctor: can view, cannot create, edit or import
        mvc.perform(get("/api/exams").session(proctor)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canManage").value(false));
        mvc.perform(get("/api/exams/" + id + "/candidates").session(proctor)).andExpect(status().isOk());
        mvc.perform(json(post("/api/exams"), create).session(proctor)).andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/exams/" + id), "{\"name\":\"x\"}").session(proctor)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/exams/" + id + "/candidates"),
                "{\"rows\":[{\"ticket\":\"A1\",\"name\":\"x\"}]}").session(proctor)).andExpect(status().isForbidden());

        // Author, administrator: cannot see exams or rosters
        for (MockHttpSession s : List.of(author, admin)) {
            mvc.perform(get("/api/exams").session(s)).andExpect(jsonPath("$.length()").value(0));
            mvc.perform(get("/api/exams/" + id).session(s)).andExpect(status().isNotFound());
            mvc.perform(get("/api/exams/" + id + "/candidates").session(s)).andExpect(status().isNotFound());
        }
        mvc.perform(json(post("/api/exams"), create).session(author)).andExpect(status().isForbidden());
        mvc.perform(get("/api/exams")).andExpect(status().isUnauthorized());
    }

    @Test
    void rosterImport_check_seats_codes_reset_remove() throws Exception {
        long id = createExam(null);
        String file = """
                准考證號,姓名,報考科系
                a115-0001,王小明,資訊工程科
                A115-0002,"李, 宜華",電機工程科
                ,沈○安,機械工程科
                A115-0004,,機械工程科
                A115-0001,重複的人,資訊工程科
                """;
        var preview = mvc.perform(multipart("/api/exams/" + id + "/candidates/preview").file(csv(file, StandardCharsets.UTF_8))
                .with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].ticket").value("A115-0001"))
                .andExpect(jsonPath("$.rows[1].name").value("李, 宜華"))
                .andExpect(jsonPath("$.problems.length()").value(3))
                .andExpect(jsonPath("$.problems[0].row").value("第 4 列"))
                .andExpect(jsonPath("$.problems[0].reason").value("缺少准考證號"))
                .andExpect(jsonPath("$.problems[1].reason").value("缺少姓名"))
                .andExpect(jsonPath("$.problems[2].reason").value("准考證號在檔案裡重複")).andReturn();
        // Checking writes nothing
        mvc.perform(get("/api/exams/" + id + "/candidates").session(owner)).andExpect(jsonPath("$.length()").value(0));

        String rows = "{\"rows\":[{\"ticket\":\"a115-0001\",\"name\":\"王小明\",\"dept\":\"資訊工程科\"},"
                + "{\"ticket\":\"A115-0002\",\"name\":\"李, 宜華\",\"dept\":\"電機工程科\"}]}";
        mvc.perform(json(post("/api/exams/" + id + "/candidates"), rows).session(owner)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.added").value(2)).andExpect(jsonPath("$.total").value(2));
        // Importing the same batch again is rejected as a whole
        mvc.perform(json(post("/api/exams/" + id + "/candidates"), rows).session(owner)).andExpect(status().isConflict());
        // Uploading the same file again flags those already on the roster
        mvc.perform(multipart("/api/exams/" + id + "/candidates/preview").file(csv(file, StandardCharsets.UTF_8))
                .with(csrf()).session(owner)).andExpect(jsonPath("$.rows.length()").value(0))
                .andExpect(jsonPath("$.problems[0].reason").value("已經在名單中"));

        var list = mvc.perform(get("/api/exams/" + id + "/candidates").session(proctor)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticket").value("A115-0001")).andExpect(jsonPath("$[0].seat").value(1))
                .andExpect(jsonPath("$[1].seat").value(2)).andExpect(jsonPath("$[0].status").value("IDLE")).andReturn();
        String code = read(list, "$[0].code");
        assertThat(code).matches("\\d{6}");
        long cid = ((Number) read(list, "$[0].id")).longValue();
        mvc.perform(get("/api/exams/" + id).session(owner)).andExpect(jsonPath("$.takers").value(2));

        // Proctors can reset login codes
        var reset = mvc.perform(post("/api/exams/" + id + "/candidates/" + cid + "/reset-code").with(csrf()).session(proctor))
                .andExpect(status().isOk()).andReturn();
        String next = read(reset, "$.code");
        assertThat(next).matches("\\d{6}");
        mvc.perform(get("/api/exams/" + id + "/candidates").session(owner)).andExpect(jsonPath("$[0].code").value(next));

        // Removing: proctors cannot, owners can
        mvc.perform(delete("/api/exams/" + id + "/candidates/" + cid).with(csrf()).session(proctor))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/exams/" + id + "/candidates/" + cid).with(csrf()).session(owner))
                .andExpect(status().isNoContent());
        // Newly imported candidates get the next seats, no duplicates
        mvc.perform(json(post("/api/exams/" + id + "/candidates"), "{\"rows\":[{\"ticket\":\"A115-0009\",\"name\":\"新同學\"}]}")
                .session(owner)).andExpect(status().isCreated());
        mvc.perform(get("/api/exams/" + id + "/candidates").session(owner)).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].seat").value(3)).andExpect(jsonPath("$[1].dept").value(""));

        mvc.perform(get("/api/admin/audit-logs").param("action", "CANDIDATE_CODE_RESET").session(admin))
                .andExpect(jsonPath("$.items[0].detail").value("A115-0001"));
    }

    @Test
    void rosterCanBeBig5Csv_withoutHeader_orExcel() throws Exception {
        long id = createExam(null);
        // Big5, no header row
        mvc.perform(multipart("/api/exams/" + id + "/candidates/preview")
                .file(csv("B001,陳柏安,電機工程科\r\nB002,林子妤,電機工程科\r\n", Charset.forName("MS950")))
                .with(csrf()).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(2)).andExpect(jsonPath("$.rows[0].name").value("陳柏安"))
                .andExpect(jsonPath("$.rows[1].dept").value("電機工程科"));

        // Excel: different column order, candidate number as a numeric cell
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var head = sheet.createRow(0);
            head.createCell(0).setCellValue("姓名");
            head.createCell(1).setCellValue("報考科系");
            head.createCell(2).setCellValue("准考證號碼");
            var r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("張家翔");
            r1.createCell(1).setCellValue("機械工程科");
            r1.createCell(2).setCellValue(1150001);
            var r2 = sheet.createRow(3);
            r2.createCell(0).setCellValue("黃詩婷");
            r2.createCell(2).setCellValue("C-002");
            wb.write(out);
            xlsx = out.toByteArray();
        }
        mvc.perform(multipart("/api/exams/" + id + "/candidates/preview")
                .file(new MockMultipartFile("file", "名單.xlsx", "application/octet-stream", xlsx)).with(csrf()).session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.problems.length()").value(0))
                .andExpect(jsonPath("$.rows[0].ticket").value("1150001")).andExpect(jsonPath("$.rows[0].name").value("張家翔"))
                .andExpect(jsonPath("$.rows[0].dept").value("機械工程科")).andExpect(jsonPath("$.rows[1].ticket").value("C-002"))
                .andExpect(jsonPath("$.rows[1].dept").value(""));

        mvc.perform(multipart("/api/exams/" + id + "/candidates/preview")
                .file(new MockMultipartFile("file", "名單.pdf", "application/pdf", new byte[] { 1, 2, 3 })).with(csrf())
                .session(owner)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("bad_file"));
        mvc.perform(multipart("/api/exams/" + id + "/candidates/preview")
                .file(new MockMultipartFile("file", "壞掉.xlsx", "application/octet-stream", new byte[] { 1, 2, 3 }))
                .with(csrf()).session(owner)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("bad_file"));
    }

    @Test
    void deletedBank_draftCannotPublish_publishedUnaffected() throws Exception {
        long draft = createExam(Instant.now().plus(1, ChronoUnit.DAYS));
        long published = createExam(Instant.now().plus(1, ChronoUnit.DAYS));
        mvc.perform(post("/api/exams/" + published + "/publish").with(csrf()).session(owner)).andExpect(status().isOk());

        mvc.perform(delete("/api/banks/" + bank).with(csrf()).session(author)).andExpect(status().isNoContent());

        mvc.perform(post("/api/exams/" + draft + "/publish").with(csrf()).session(owner))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("no_bank"));
        mvc.perform(get("/api/exams/" + published).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bankName").value("入學題庫")).andExpect(jsonPath("$.questionCount").value(1));
    }
}
