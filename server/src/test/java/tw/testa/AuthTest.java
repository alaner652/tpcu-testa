package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

class AuthTest extends IntegrationTestBase {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void 未登入一律401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mvc.perform(get("/api/units")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/staff")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void 登入成功後可以取得自己的資料() throws Exception {
        MockHttpSession s = adminSession();
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.systemAdmin").value(true));
    }

    @Test
    void 帳號或密碼錯誤回同樣的401() throws Exception {
        mvc.perform(json(post("/api/auth/login"), "{\"email\":\"admin@test.tw\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("帳號或密碼不正確，請再試一次。"));
        mvc.perform(json(post("/api/auth/login"), "{\"email\":\"nobody@test.tw\",\"password\":\"nope-nope\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("帳號或密碼不正確，請再試一次。"));
    }

    @Test
    void 沒帶CSRF的寫入請求被拒絕() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.tw\",\"password\":\"admin-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 連續輸錯密碼會鎖定_重設密碼後解鎖() throws Exception {
        MockHttpSession admin = adminSession();
        Object[] staff = createStaff(admin, "lock@test.tw", "鎖定測試");
        String bad = "{\"email\":\"lock@test.tw\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(json(post("/api/auth/login"), bad)).andExpect(status().isUnauthorized());
        }
        // 鎖定後連正確密碼也進不去
        mvc.perform(json(post("/api/auth/login"),
                "{\"email\":\"lock@test.tw\",\"password\":\"%s\"}".formatted(staff[1])))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("locked"));

        var reset = mvc.perform(post("/api/admin/staff/" + staff[0] + "/reset-password")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .session(admin)).andExpect(status().isOk()).andReturn();
        login("lock@test.tw", read(reset, "$.temporaryPassword"));
    }

    @Test
    void 新帳號要先改密碼_改完舊密碼失效() throws Exception {
        MockHttpSession admin = adminSession();
        Object[] staff = createStaff(admin, "newbie@test.tw", "新人");
        MockHttpSession s = login("newbie@test.tw", (String) staff[1]);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(jsonPath("$.mustChangePassword").value(true));

        mvc.perform(json(post("/api/auth/password"),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"short\"}".formatted(staff[1])).session(s))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("weak_password"));
        mvc.perform(json(post("/api/auth/password"),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"brand-new-password\"}".formatted(staff[1])).session(s))
                .andExpect(status().isNoContent());

        // 目前這個 Session 繼續有效
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        mvc.perform(json(post("/api/auth/login"),
                "{\"email\":\"newbie@test.tw\",\"password\":\"%s\"}".formatted(staff[1])))
                .andExpect(status().isUnauthorized());
        login("newbie@test.tw", "brand-new-password");
    }

    @Test
    void 改密碼會登出其他裝置() throws Exception {
        MockHttpSession admin = adminSession();
        Object[] staff = createStaff(admin, "two-devices@test.tw", "兩台裝置");
        MockHttpSession a = login("two-devices@test.tw", (String) staff[1]);
        MockHttpSession b = login("two-devices@test.tw", (String) staff[1]);
        mvc.perform(json(post("/api/auth/password"),
                "{\"currentPassword\":\"%s\",\"newPassword\":\"brand-new-password\"}".formatted(staff[1])).session(a))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(a)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(b)).andExpect(status().isUnauthorized());
    }

    @Test
    void 停用帳號後現有登入立即失效() throws Exception {
        MockHttpSession admin = adminSession();
        Object[] staff = createStaff(admin, "leaver@test.tw", "離職");
        MockHttpSession s = login("leaver@test.tw", (String) staff[1]);
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isOk());

        mvc.perform(json(patch("/api/admin/staff/" + staff[0]), "{\"active\":false}").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/auth/login"),
                "{\"email\":\"leaver@test.tw\",\"password\":\"%s\"}".formatted(staff[1])))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 登出後Session失效() throws Exception {
        MockHttpSession s = adminSession();
        mvc.perform(json(post("/api/auth/logout"), "").session(s)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(s)).andExpect(status().isUnauthorized());
    }

    @Test
    void 系統管理員不能停用自己() throws Exception {
        MockHttpSession admin = adminSession();
        Number id = read(mvc.perform(get("/api/auth/me").session(admin)).andReturn(), "$.id");
        mvc.perform(json(patch("/api/admin/staff/" + id), "{\"active\":false}").session(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(json(patch("/api/admin/staff/" + id), "{\"systemAdmin\":false}").session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 操作紀錄有寫入而且不能改() throws Exception {
        MockHttpSession admin = adminSession();
        createStaff(admin, "audited@test.tw", "紀錄");
        mvc.perform(get("/api/admin/audit-logs").param("action", "STAFF_CREATED").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].actorLabel").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.items[0].targetType").value("staff"));

        assertThat(jdbc.queryForObject("select count(*) from audit_log", Long.class)).isPositive();
        assertThatThrownBy(() -> jdbc.update("update audit_log set action = 'X'")).hasMessageContaining("只能新增");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log")).hasMessageContaining("只能新增");
    }
}
