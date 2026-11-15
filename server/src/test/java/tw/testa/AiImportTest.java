package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.sun.net.httpserver.HttpServer;

/** AI question import against a stand-in model endpoint: permissions, file handling and the checks on the model's output. */
class AiImportTest extends IntegrationTestBase {
    private static int seq;
    static final HttpServer MODEL;
    /** What the stand-in model answers with (the content of the chat completion). */
    static final AtomicReference<String> ANSWER = new AtomicReference<>("{\"questions\":[]}");
    static final AtomicReference<String> LAST_REQUEST = new AtomicReference<>("");

    static {
        try {
            MODEL = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MODEL.createContext("/v1/chat/completions", exchange -> {
                LAST_REQUEST.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                String content = ANSWER.get().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
                byte[] body = "{\"choices\":[{\"message\":{\"content\":\"%s\"}}]}".formatted(content).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            MODEL.start();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("testa.ai.base-url", () -> "http://127.0.0.1:" + MODEL.getAddress().getPort());
    }

    static final String PAPER = """
            一、單選題（每題5分）
            1. 下列何者為質數？ (A)21 (B)27 (C)29 (D)33
            二、多選題（每題 6 分）
            2. 下列哪些數是偶數？ A. 2　B. 7　C. 10　D. 15　E. 18
            三、填充題（每格5分）
            3. 台灣最高的山是＿＿＿＿山。
            四、問答題（20分）
            4. 請說明微積分在日常生活中的應用。（300字以內）
            5. 下列何者正確？ (A)甲 (B)乙
            【答案】1.C 2.ACE 3.玉 5.D
            """;

    long unit;
    MockHttpSession author;
    MockHttpSession grader;
    MockHttpSession otherAuthor;

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        MockHttpSession admin = adminSession();
        unit = createUnit(admin, "AI 匯入測試單位 " + n);
        author = staffWith(admin, "ai-author" + n + "@test.tw", "AUTHOR");
        grader = staffWith(admin, "ai-grader" + n + "@test.tw", "GRADER");
        otherAuthor = staffWith(admin, "ai-author-b" + n + "@test.tw", "AUTHOR");
    }

    private MockHttpSession staffWith(MockHttpSession admin, String email, String role) throws Exception {
        Object[] s = createStaff(admin, email, email);
        grant(admin, unit, email, role);
        return login(email, (String) s[1]);
    }

    private static MockMultipartFile docx(String text) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : text.split("\n")) doc.createParagraph().createRun().setText(line);
            doc.write(out);
            return new MockMultipartFile("file", "考卷.docx", "application/octet-stream", out.toByteArray());
        }
    }

    private String start(MockHttpSession by, MockMultipartFile file) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/ai/question-import").file(file).param("unitId", String.valueOf(unit))
                .with(csrf()).session(by)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("running")).andReturn();
        return read(r, "$.id");
    }

    private MvcResult finished(MockHttpSession by, String id) throws Exception {
        for (int i = 0; i < 100; i++) {
            MvcResult r = mvc.perform(get("/api/ai/question-import/" + id).session(by)).andExpect(status().isOk()).andReturn();
            if (!"running".equals(read(r, "$.state"))) return r;
            Thread.sleep(50);
        }
        throw new AssertionError("import did not finish");
    }

    @Test
    void authorImportsWordFile_modelOutputIsCheckedByCode() throws Exception {
        mvc.perform(get("/api/ai/status").session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.questionImport").value(true));
        ANSWER.set("""
                {"questions":[
                 {"type":"single","stem":"下列何者為質數？","points":5,"choices":["(A)21","(B)27","(C)29","(D)33"],"correct":["C"],"blankAnswers":[],"wordLimit":null},
                 {"type":"multiple","stem":"下列哪些數是偶數？","points":6,"choices":["2","7","10","15","18"],"correct":["ACE"],"blankAnswers":[],"wordLimit":null},
                 {"type":"blank","stem":"台灣最高的山是＿＿＿＿山。","points":5,"choices":[],"correct":[],"blankAnswers":["玉"],"wordLimit":null},
                 {"type":"essay","stem":"請說明微積分在日常生活中的應用。","points":20,"choices":[],"correct":[],"blankAnswers":[],"wordLimit":300},
                 {"type":"single","stem":"下列何者正確？","points":5,"choices":["甲","乙"],"correct":["D"],"blankAnswers":[],"wordLimit":null},
                 {"type":"single","stem":"這一題是模型自己編出來的","points":0,"choices":["一","二"],"correct":["A"],"blankAnswers":[],"wordLimit":null},
                 {"type":"other","stem":"不支援的題型","points":5,"choices":[],"correct":[],"blankAnswers":[],"wordLimit":null}
                ]}""");

        String id = start(author, docx(PAPER));
        MvcResult r = finished(author, id);
        assertThat((String) read(r, "$.state")).isEqualTo("done");
        assertThat((Integer) read(r, "$.questions.length()")).isEqualTo(6);
        // The paper text reached the model
        assertThat(LAST_REQUEST.get()).contains("下列何者為質數").contains("json_schema");

        // Choice labels stripped, answer letters turned into indexes
        assertThat((String) read(r, "$.questions[0].choices[2]")).isEqualTo("29");
        assertThat((Object) read(r, "$.questions[0].correct")).hasToString("[2]");
        assertThat((Object) read(r, "$.questions[0].note")).isNull();
        assertThat((Object) read(r, "$.questions[1].correct")).hasToString("[0,2,4]");
        assertThat((Object) read(r, "$.questions[2].blanks")).hasToString("[[\"玉\"]]");
        assertThat((Integer) read(r, "$.questions[3].wordLimit")).isEqualTo(300);
        // An answer that points at no choice is dropped with a note, never guessed
        assertThat((Object) read(r, "$.questions[4].correct")).hasToString("[]");
        assertThat((String) read(r, "$.questions[4].note")).contains("答案對不上選項");
        // A stem that is not in the paper, and points the model could not read
        assertThat((String) read(r, "$.questions[5].note")).contains("題目文字和原檔不同").contains("看不出配分");
        assertThat((Double) read(r, "$.questions[5].points")).isEqualTo(5.0);
    }

    @Test
    void onlyAuthorsOfTheUnitCanImport_andOnlyTheStarterReadsTheJob() throws Exception {
        mvc.perform(multipart("/api/ai/question-import").file(docx(PAPER)).param("unitId", String.valueOf(unit))
                .with(csrf()).session(grader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());

        ANSWER.set("{\"questions\":[]}");
        String id = start(author, docx(PAPER));
        mvc.perform(get("/api/ai/question-import/" + id).session(otherAuthor)).andExpect(status().isNotFound());
        // No questions found is a failure the author can read
        MvcResult r = finished(author, id);
        assertThat((String) read(r, "$.state")).isEqualTo("failed");
        assertThat((String) read(r, "$.error")).contains("沒有從這份考卷辨識出題目");
    }

    @Test
    void rejectsUnsupportedEmptyAndOversizedFiles() throws Exception {
        var doc = new MockMultipartFile("file", "舊版.doc", "application/msword", new byte[] { 1, 2, 3 });
        mvc.perform(multipart("/api/ai/question-import").file(doc).param("unitId", String.valueOf(unit)).with(csrf()).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unsupported_file"));
        var broken = new MockMultipartFile("file", "壞掉.docx", "application/octet-stream", new byte[] { 1, 2, 3 });
        mvc.perform(multipart("/api/ai/question-import").file(broken).param("unitId", String.valueOf(unit)).with(csrf()).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("unreadable_file"));
        var empty = new MockMultipartFile("file", "空白.txt", "text/plain", "  \n ".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ai/question-import").file(empty).param("unitId", String.valueOf(unit)).with(csrf()).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("empty_file"));
        var huge = new MockMultipartFile("file", "很長.txt", "text/plain", "題".repeat(8001).getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ai/question-import").file(huge).param("unitId", String.valueOf(unit)).with(csrf()).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("file_too_long"));
    }
}

/** Without a model endpoint the feature is off (the default). */
class AiImportDisabledTest extends IntegrationTestBase {
    @Test
    void statusIsOff_andImportIsRefused() throws Exception {
        MockHttpSession admin = adminSession();
        long unit = createUnit(admin, "AI 關閉測試單位");
        Object[] s = createStaff(admin, "ai-off@test.tw", "作者");
        grant(admin, unit, "ai-off@test.tw", "AUTHOR");
        MockHttpSession author = login("ai-off@test.tw", (String) s[1]);

        mvc.perform(get("/api/ai/status").session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.questionImport").value(false));
        var file = new MockMultipartFile("file", "考卷.txt", "text/plain", "1. 題目".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ai/question-import").file(file).param("unitId", String.valueOf(unit)).with(csrf()).session(author))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ai_disabled"));
    }
}
