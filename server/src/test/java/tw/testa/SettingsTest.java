package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.sun.net.httpserver.HttpServer;

import tw.testa.settings.SettingsService;

/** System settings in the admin pages: who can change them, validation, the audit trail, and that AI import follows them. */
class SettingsTest extends IntegrationTestBase {
    private static int seq;
    static final HttpServer MODEL;
    static final String KEY = "secret-key-123";

    static {
        try {
            MODEL = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MODEL.createContext("/v1/models", exchange -> {
                boolean allowed = ("Bearer " + KEY).equals(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] body = "{\"data\":[{\"id\":\"qwen3-4b\"},{\"id\":\"other-model\"}]}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(allowed ? 200 : 401, allowed ? body.length : -1);
                if (allowed) exchange.getResponseBody().write(body);
                exchange.close();
            });
            MODEL.createContext("/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                String content = "{\\\"questions\\\":[{\\\"type\\\":\\\"essay\\\",\\\"stem\\\":\\\"請說明動機。\\\",\\\"points\\\":10,"
                        + "\\\"choices\\\":[],\\\"correct\\\":[],\\\"blankAnswers\\\":[],\\\"wordLimit\\\":null}]}";
                byte[] body = "{\"choices\":[{\"message\":{\"content\":\"%s\"}}]}".formatted(content).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            MODEL.start();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SettingsService settings;

    MockHttpSession admin;
    MockHttpSession author;
    long unit;

    @BeforeEach
    void setUp() throws Exception {
        int n = ++seq;
        admin = adminSession();
        unit = createUnit(admin, "設定測試單位 " + n);
        String email = "settings-author" + n + "@test.tw";
        Object[] s = createStaff(admin, email, email);
        grant(admin, unit, email, "AUTHOR");
        author = login(email, (String) s[1]);
    }

    /** Settings are shared by every test on this database: put the defaults back. */
    @AfterEach
    void restoreDefaults() {
        jdbc.update("delete from app_setting");
        settings.reload();
    }

    private static String url() {
        return "http://127.0.0.1:" + MODEL.getAddress().getPort();
    }

    private static String body(boolean aiEnabled, String aiUrl, String aiKey, int flag, int years, int failures, int minutes) {
        return "{\"aiEnabled\":%s,\"aiBaseUrl\":\"%s\",\"aiModel\":\"qwen3-4b\",\"aiApiKey\":%s,\"flagPercent\":%d,\"retentionYears\":%d,\"loginMaxFailures\":%d,\"loginLockMinutes\":%d,\"staffIdleMinutes\":480,\"aiTimeoutSeconds\":300}"
                .formatted(aiEnabled, aiUrl, aiKey == null ? "null" : "\"" + aiKey + "\"", flag, years, failures, minutes);
    }

    @Test
    void administratorReadsDefaultsAndChangesThem_auditedWithoutTheKey() throws Exception {
        mvc.perform(get("/api/admin/settings").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.enabled").value(false)).andExpect(jsonPath("$.ai.apiKeySet").value(false))
                .andExpect(jsonPath("$.flagPercent").value(20)).andExpect(jsonPath("$.retentionYears").value(1))
                .andExpect(jsonPath("$.loginMaxFailures").value(3)).andExpect(jsonPath("$.loginLockMinutes").value(15))
                .andExpect(jsonPath("$.staffIdleMinutes").value(480)).andExpect(jsonPath("$.aiTimeoutSeconds").value(300));

        MvcResult r = mvc.perform(json(put("/api/admin/settings"), body(true, url(), KEY, 15, 2, 5, 10)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ai.enabled").value(true))
                .andExpect(jsonPath("$.ai.baseUrl").value(url())).andExpect(jsonPath("$.ai.model").value("qwen3-4b"))
                .andExpect(jsonPath("$.ai.apiKeySet").value(true)).andExpect(jsonPath("$.flagPercent").value(15))
                .andExpect(jsonPath("$.retentionYears").value(2)).andExpect(jsonPath("$.loginMaxFailures").value(5))
                .andExpect(jsonPath("$.loginLockMinutes").value(10)).andReturn();
        // The key is write-only
        assertThat(r.getResponse().getContentAsString()).doesNotContain(KEY);
        assertThat(settings.flagRatio()).isEqualTo(0.15);
        assertThat(settings.ai().apiKey()).isEqualTo(KEY);

        // Saving again without a key keeps the stored one; nothing changed, so nothing new is audited
        mvc.perform(json(put("/api/admin/settings"), body(true, url(), null, 15, 2, 5, 10)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ai.apiKeySet").value(true));

        MvcResult log = mvc.perform(get("/api/admin/audit-logs").param("action", "SETTINGS_CHANGED").session(admin))
                .andExpect(status().isOk()).andReturn();
        String detail = read(log, "$.items[0].detail");
        assertThat(detail).contains("開啟 AI 匯入題目").contains("評分差距提醒 20% 改為 15%").contains("資料保存年數 1 年 改為 2 年")
                .contains("登入錯誤上限 3 次 改為 5 次").contains("更新 AI API 金鑰").doesNotContain(KEY);
        assertThat(log.getResponse().getContentAsString()).doesNotContain(KEY);

        // The page shows the defaults next to each field and who changed the settings last
        mvc.perform(get("/api/admin/settings").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.defaults.flagPercent").value(20)).andExpect(jsonPath("$.defaults.loginMaxFailures").value(3))
                .andExpect(jsonPath("$.defaults.staffIdleMinutes").value(480))
                .andExpect(jsonPath("$.lastChange.by").value("Admin"))
                .andExpect(jsonPath("$.lastChange.detail").value(org.hamcrest.Matchers.containsString("評分差距提醒 20% 改為 15%")));
    }

    @Test
    void staffSignInFollowsTheIdleSetting() throws Exception {
        assertThat(admin.getMaxInactiveInterval()).isEqualTo(480 * 60);
        mvc.perform(json(put("/api/admin/settings"), body(false, "", null, 20, 1, 3, 15).replace("\"staffIdleMinutes\":480", "\"staffIdleMinutes\":30"))
                .session(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.staffIdleMinutes").value(30));
        assertThat(adminSession().getMaxInactiveInterval()).isEqualTo(30 * 60);
    }

    @Test
    void onlyAdministratorsSeeOrChangeSettings_andInvalidValuesAreRefused() throws Exception {
        mvc.perform(get("/api/admin/settings").session(author)).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/admin/settings"), body(false, "", null, 20, 1, 3, 15)).session(author))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/admin/settings/ai/test"), "{\"baseUrl\":\"%s\"}".formatted(url())).session(author))
                .andExpect(status().isForbidden());

        mvc.perform(json(put("/api/admin/settings"), body(true, "", null, 20, 1, 3, 15)).session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ai_url_required"));
        mvc.perform(json(put("/api/admin/settings"), body(false, "ftp://example", null, 20, 1, 3, 15)).session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ai_url_invalid"));
        mvc.perform(json(put("/api/admin/settings"), body(false, "", null, 2, 1, 3, 15)).session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/admin/settings"), body(false, "", null, 20, 0, 3, 15)).session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/admin/settings"), body(false, "", null, 20, 1, 1, 15)).session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/api/admin/settings"), body(false, "", null, 20, 1, 3, 15)
                .replace("\"staffIdleMinutes\":480", "\"staffIdleMinutes\":0")).session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void connectionTestExplainsWhatIsWrong() throws Exception {
        mvc.perform(json(post("/api/admin/settings/ai/test"), "{\"baseUrl\":\"%s\",\"apiKey\":\"%s\"}".formatted(url(), KEY)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.models[0]").value("qwen3-4b")).andExpect(jsonPath("$.models.length()").value(2));
        mvc.perform(json(post("/api/admin/settings/ai/test"), "{\"baseUrl\":\"%s\",\"apiKey\":\"wrong\"}".formatted(url())).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("API 金鑰")));
        // Nothing listens on port 1
        mvc.perform(json(post("/api/admin/settings/ai/test"), "{\"baseUrl\":\"http://127.0.0.1:1\"}").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("連不上")));
    }

    @Test
    void aiImportFollowsTheSettings() throws Exception {
        mvc.perform(get("/api/ai/status").session(author)).andExpect(jsonPath("$.questionImport").value(false));

        mvc.perform(json(put("/api/admin/settings"), body(true, url(), KEY, 20, 1, 3, 15)).session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/ai/status").session(author)).andExpect(jsonPath("$.questionImport").value(true));
        var file = new MockMultipartFile("file", "考卷.txt", "text/plain", "1. 請說明動機。".getBytes(StandardCharsets.UTF_8));
        MvcResult started = mvc.perform(multipart("/api/ai/question-import").file(file).param("unitId", String.valueOf(unit))
                .with(csrf()).session(author)).andExpect(status().isAccepted()).andReturn();
        String id = read(started, "$.id");
        String state = "running";
        for (int i = 0; i < 100 && state.equals("running"); i++) {
            Thread.sleep(50);
            state = read(mvc.perform(get("/api/ai/question-import/" + id).session(author)).andReturn(), "$.state");
        }
        assertThat(state).isEqualTo("done");

        // Switching it off keeps the address but hides the feature again
        mvc.perform(json(put("/api/admin/settings"), body(false, url(), null, 20, 1, 3, 15)).session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/ai/status").session(author)).andExpect(jsonPath("$.questionImport").value(false));
    }
}
