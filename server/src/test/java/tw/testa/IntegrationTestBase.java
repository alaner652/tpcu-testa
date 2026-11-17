package tw.testa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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

/** Shared integration test setup: real PostgreSQL (Testcontainers) and the full Spring Security filter chain. */
@SpringBootTest(properties = {
        "testa.login.max-failures=3",
        // MockMvc tests carry sign-in state in a MockHttpSession; JDBC sessions are covered over real HTTP by deploy/smoke-test.py
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

    /** The first system administrator is created once, through first-run setup, by whichever test runs first. */
    @BeforeEach
    void ensureAdmin() throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/setup")).andExpect(status().isOk()).andReturn();
        if (!(Boolean) read(r, "$.required")) return;
        mvc.perform(json(post("/api/auth/setup"), "{\"email\":\"%s\",\"name\":\"Admin\",\"password\":\"%s\"}"
                .formatted(ADMIN_EMAIL, ADMIN_PASSWORD))).andExpect(status().isOk());
    }

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

    /** Creates a staff account as the system administrator and returns { id, temporary password }. */
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

    /** Issues login codes for the whole roster, as printing slips does; `$[i].code` follows seat order. */
    protected MvcResult issueCodes(MockHttpSession by, long examId) throws Exception {
        java.util.List<Number> ids = read(mvc.perform(get("/api/exams/" + examId + "/candidates").session(by)).andReturn(), "$[*].id");
        return mvc.perform(json(post("/api/exams/" + examId + "/candidates/codes"), "{\"ids\":" + ids + "}").session(by))
                .andExpect(status().isOk()).andReturn();
    }
}
