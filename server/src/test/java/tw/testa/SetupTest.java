package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import tw.testa.auth.SetupService;
import tw.testa.common.ApiException;

class SetupTest extends IntegrationTestBase {
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SetupService setup;

    private static String body(String email, String password) {
        return "{\"email\":\"%s\",\"name\":\"First\",\"password\":\"%s\"}".formatted(email, password);
    }

    @Test
    void setupIsClosedOnceAnAdministratorExists() throws Exception {
        mvc.perform(get("/api/auth/setup")).andExpect(jsonPath("$.required").value(false));
        mvc.perform(json(post("/api/auth/setup"), body("late@test.tw", "late-password")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("setup_done"));
    }

    @Test
    void firstAdmin_needsValidPassword_andOnlyOneWinsConcurrently() throws Exception {
        // Simulate a fresh installation by demoting the existing administrators for the duration of the test
        List<Long> admins = jdbc.queryForList("select id from staff where is_system_admin", Long.class);
        jdbc.update("update staff set is_system_admin = false");
        try {
            mvc.perform(get("/api/auth/setup")).andExpect(jsonPath("$.required").value(true));
            mvc.perform(json(post("/api/auth/setup"), body("first@test.tw", "short")))
                    .andExpect(status().isBadRequest());

            var pool = Executors.newFixedThreadPool(6);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String email = "race" + i + "@test.tw";
                Callable<Boolean> task = () -> {
                    try {
                        setup.createFirstAdmin(email, "Race", "race-password");
                        return true;
                    } catch (ApiException e) {
                        assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                        return false;
                    }
                };
                results.add(pool.submit(task));
            }
            int created = 0;
            for (Future<Boolean> f : results) if (f.get()) created++;
            pool.shutdown();

            assertThat(created).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from staff where is_system_admin", Long.class)).isOne();
            assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'SYSTEM_SETUP'", Long.class))
                    .isPositive();
            mvc.perform(get("/api/auth/setup")).andExpect(jsonPath("$.required").value(false));
        } finally {
            jdbc.update("update staff set is_system_admin = false, active = false where email like 'race%@test.tw'");
            for (Long id : admins) jdbc.update("update staff set is_system_admin = true where id = ?", id);
        }
    }
}
