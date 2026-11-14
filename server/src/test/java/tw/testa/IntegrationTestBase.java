package tw.testa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

/** 整合測試共用：真的 PostgreSQL（Testcontainers）、完整的 Spring Security 過濾鏈。 */
@SpringBootTest(properties = {
        "testa.bootstrap-admin.email=admin@test.tw",
        "testa.bootstrap-admin.password=admin-password",
        "testa.login.max-failures=3",
        // MockMvc 的測試用 MockHttpSession 帶登入狀態；存資料庫的 Session 由 deploy/smoke-test.py 走真的 HTTP 驗證
        "spring.autoconfigure.exclude=org.springframework.boot.session.jdbc.autoconfigure.JdbcSessionAutoConfiguration",
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {
    protected static final String ADMIN_EMAIL = "admin@test.tw";
    protected static final String ADMIN_PASSWORD = "admin-password";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    protected MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(json(post("/api/auth/login"), "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))
                .session(session)).andExpect(status().isOk());
        return session;
    }

    protected MockHttpSession adminSession() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
    }

    protected static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    /** 以系統管理員建立一個教職員帳號，回傳 { id, 臨時密碼 }。 */
    protected Object[] createStaff(MockHttpSession admin, String email, String name) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/admin/staff"),
                "{\"email\":\"%s\",\"name\":\"%s\"}".formatted(email, name)).session(admin))
                .andExpect(status().isCreated()).andReturn();
        return new Object[] { ((Number) read(r, "$.staff.id")).longValue(), read(r, "$.temporaryPassword") };
    }

    protected long createUnit(MockHttpSession admin, String name) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/units"), "{\"name\":\"%s\",\"type\":\"ADMISSION\"}".formatted(name))
                .session(admin)).andExpect(status().isCreated()).andReturn();
        return ((Number) read(r, "$.id")).longValue();
    }

    protected void grant(MockHttpSession by, long unitId, String email, String role) throws Exception {
        mvc.perform(json(post("/api/units/" + unitId + "/members"),
                "{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)).session(by))
                .andExpect(status().isCreated());
    }
}
