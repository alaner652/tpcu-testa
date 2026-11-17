package tw.testa;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Permission checks: roles belong to units. */
class UnitAccessTest extends IntegrationTestBase {

    @Test
    void regularStaffCannotOpenAdmin() throws Exception {
        MockHttpSession admin = adminSession();
        Object[] t = createStaff(admin, "plain@test.tw", "一般教師");
        MockHttpSession s = login("plain@test.tw", (String) t[1]);

        mvc.perform(get("/api/admin/staff").session(s)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        mvc.perform(get("/api/admin/audit-logs").session(s)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/admin/staff"), "{\"email\":\"x@test.tw\",\"name\":\"x\"}").session(s))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/units"), "{\"name\":\"偷建單位\",\"type\":\"ADMISSION\"}").session(s))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolesBelongToUnits() throws Exception {
        MockHttpSession admin = adminSession();
        long unitA = createUnit(admin, "A 單位");
        long unitB = createUnit(admin, "B 單位");
        Object[] owner = createStaff(admin, "owner-a@test.tw", "A 負責人");
        Object[] teacher = createStaff(admin, "teacher-a@test.tw", "A 教師");

        // The administrator makes someone owner of unit A
        grant(admin, unitA, "owner-a@test.tw", "OWNER");
        MockHttpSession ownerS = login("owner-a@test.tw", (String) owner[1]);

        // Owners see only their own units
        mvc.perform(get("/api/units").session(ownerS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("A 單位"));
        mvc.perform(get("/api/auth/me").session(ownerS))
                .andExpect(jsonPath("$.units[0].roles[0]").value("OWNER"));

        // Owners assign roles in their unit, not in others
        grant(ownerS, unitA, "teacher-a@test.tw", "AUTHOR");
        grant(ownerS, unitA, "teacher-a@test.tw", "GRADER");
        mvc.perform(json(post("/api/units/" + unitB + "/members"),
                "{\"email\":\"teacher-a@test.tw\",\"role\":\"AUTHOR\"}").session(ownerS))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/units/" + unitB + "/members").session(ownerS)).andExpect(status().isForbidden());

        // Duplicate assignment
        mvc.perform(json(post("/api/units/" + unitA + "/members"),
                "{\"email\":\"teacher-a@test.tw\",\"role\":\"AUTHOR\"}").session(ownerS))
                .andExpect(status().isConflict());
        // Unknown staff member
        mvc.perform(json(post("/api/units/" + unitA + "/members"),
                "{\"email\":\"ghost@test.tw\",\"role\":\"AUTHOR\"}").session(ownerS))
                .andExpect(status().isBadRequest());

        // A teacher with a role who is not an owner: sees the unit, cannot manage members
        MockHttpSession teacherS = login("teacher-a@test.tw", (String) teacher[1]);
        mvc.perform(get("/api/units").session(teacherS)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/auth/me").session(teacherS))
                .andExpect(jsonPath("$.units[0].roles.length()").value(2));
        mvc.perform(get("/api/units/" + unitA + "/members").session(teacherS)).andExpect(status().isForbidden());

        // Member list
        var members = mvc.perform(get("/api/units/" + unitA + "/members").session(ownerS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andReturn();

        // An owner cannot remove the last owner (themselves)
        Number ownerRoleId = ((java.util.List<Number>) read(members, "$[?(@.role=='OWNER')].id")).get(0);
        mvc.perform(delete("/api/units/" + unitA + "/members/" + ownerRoleId).with(csrf()).session(ownerS))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("last_owner"));

        // Revoking a role takes effect without signing in again
        Number authorRoleId = ((java.util.List<Number>) read(members, "$[?(@.role=='AUTHOR')].id")).get(0);
        mvc.perform(delete("/api/units/" + unitA + "/members/" + authorRoleId).with(csrf()).session(ownerS))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(teacherS))
                .andExpect(jsonPath("$.units[0].roles.length()").value(1))
                .andExpect(jsonPath("$.units[0].roles[0]").value("GRADER"));

        // Once the administrator removes the owner, they lose management at once
        mvc.perform(delete("/api/units/" + unitA + "/members/" + ownerRoleId).with(csrf()).session(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/units/" + unitA + "/members").session(ownerS)).andExpect(status().isForbidden());
    }

    @Test
    void disablingAUnitRevokesItsRoles() throws Exception {
        MockHttpSession admin = adminSession();
        long unit = createUnit(admin, "要停用的單位");
        Object[] owner = createStaff(admin, "owner-c@test.tw", "C 負責人");
        grant(admin, unit, "owner-c@test.tw", "OWNER");
        MockHttpSession s = login("owner-c@test.tw", (String) owner[1]);
        mvc.perform(get("/api/units/" + unit + "/members").session(s)).andExpect(status().isOk());

        mvc.perform(json(patch("/api/units/" + unit), "{\"active\":false}").session(admin))
                .andExpect(status().isOk());

        mvc.perform(get("/api/units/" + unit + "/members").session(s)).andExpect(status().isForbidden());
        mvc.perform(get("/api/units").session(s)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unitNamesAreUnique_inputIsValidated() throws Exception {
        MockHttpSession admin = adminSession();
        createUnit(admin, "重複單位");
        mvc.perform(json(post("/api/units"), "{\"name\":\"重複單位\",\"type\":\"ADMISSION\"}").session(admin))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/units"), "{\"name\":\"\",\"type\":\"ADMISSION\"}").session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("validation"));
        mvc.perform(json(post("/api/admin/staff"), "{\"email\":\"not-an-email\",\"name\":\"x\"}").session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void accountsCanJoinUnitsWhenCreated() throws Exception {
        MockHttpSession admin = adminSession();
        long unit = createUnit(admin, "建立即加入");

        mvc.perform(json(post("/api/admin/staff"), """
                {"email":"joined@test.tw","name":"一步到位","memberships":[{"unitId":%d,"roles":["OWNER","PROCTOR"]}]}
                """.formatted(unit)).session(admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staff.roles.length()").value(2))
                .andExpect(jsonPath("$.temporaryPassword").isString());
        mvc.perform(get("/api/units/" + unit + "/members").session(admin))
                .andExpect(jsonPath("$.length()").value(2));

        // An unknown unit creates nothing
        mvc.perform(json(post("/api/admin/staff"),
                "{\"email\":\"lost@test.tw\",\"name\":\"x\",\"memberships\":[{\"unitId\":999999,\"roles\":[\"AUTHOR\"]}]}")
                .session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/units/" + unit + "/staff-search").param("q", "lost@").session(admin))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void batchCreationIsAllOrNothing() throws Exception {
        MockHttpSession admin = adminSession();
        long unit = createUnit(admin, "批次單位");
        createStaff(admin, "batch-taken@test.tw", "已有帳號");

        mvc.perform(json(post("/api/admin/staff/batch"), """
                {"people":[{"email":"batch-1@test.tw","name":"一號"},{"email":"BATCH-1@test.tw","name":"重複"},
                {"email":"batch-taken@test.tw","name":"已有"}]}
                """).session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("batch_invalid"))
                .andExpect(jsonPath("$.errors['people[1].email']").exists())
                .andExpect(jsonPath("$.errors['people[2].email']").exists());
        mvc.perform(json(post("/api/admin/staff/batch"), "{\"people\":[{\"email\":\"bad\",\"name\":\"x\"}]}")
                .session(admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['people[0].email']").exists());
        mvc.perform(get("/api/units/" + unit + "/staff-search").param("q", "batch-1").session(admin))
                .andExpect(jsonPath("$.length()").value(0));

        var r = mvc.perform(json(post("/api/admin/staff/batch"), """
                {"people":[{"email":"batch-1@test.tw","name":"一號"},{"email":"batch-2@test.tw","name":"二號"}],
                "memberships":[{"unitId":%d,"roles":["AUTHOR","GRADER"]}]}
                """.formatted(unit)).session(admin))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].staff.roles.length()").value(2)).andReturn();
        // Each new account can sign in with its own temporary password
        login("batch-2@test.tw", read(r, "$[1].temporaryPassword"));
        mvc.perform(get("/api/units/" + unit + "/members").session(admin))
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(json(post("/api/admin/staff/batch"), "{\"people\":[]}").session(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownersFindColleaguesAndGrantSeveralRoles() throws Exception {
        MockHttpSession admin = adminSession();
        long unit = createUnit(admin, "搜尋單位");
        long other = createUnit(admin, "別人的單位");
        Object[] owner = createStaff(admin, "search-owner@test.tw", "搜尋負責人");
        createStaff(admin, "search-teacher@test.tw", "蔡搜尋");
        Object[] gone = createStaff(admin, "search-gone@test.tw", "蔡停用");
        grant(admin, unit, "search-owner@test.tw", "OWNER");
        mvc.perform(json(patch("/api/admin/staff/" + gone[0]), "{\"active\":false}").session(admin))
                .andExpect(status().isOk());
        MockHttpSession ownerS = login("search-owner@test.tw", (String) owner[1]);

        // By name or email, active accounts only, name and email only
        mvc.perform(get("/api/units/" + unit + "/staff-search").param("q", "蔡").session(ownerS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("search-teacher@test.tw"))
                .andExpect(jsonPath("$[0].systemAdmin").doesNotExist());
        mvc.perform(get("/api/units/" + unit + "/staff-search").param("q", "SEARCH-TEACHER").session(ownerS))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/units/" + other + "/staff-search").param("q", "蔡").session(ownerS))
                .andExpect(status().isForbidden());

        // Several roles in one request; held roles are skipped, all held is a conflict
        mvc.perform(json(post("/api/units/" + unit + "/members"),
                "{\"email\":\"search-teacher@test.tw\",\"roles\":[\"AUTHOR\",\"GRADER\"]}").session(ownerS))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(json(post("/api/units/" + unit + "/members"),
                "{\"email\":\"search-teacher@test.tw\",\"roles\":[\"GRADER\",\"PROCTOR\"]}").session(ownerS))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(json(post("/api/units/" + unit + "/members"),
                "{\"email\":\"search-teacher@test.tw\",\"roles\":[\"AUTHOR\"]}").session(ownerS))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/units/" + unit + "/members"),
                "{\"email\":\"search-teacher@test.tw\"}").session(ownerS))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminsActOnSeveralAccountsAtOnce() throws Exception {
        MockHttpSession admin = adminSession();
        long unit = createUnit(admin, "批次操作單位");
        long a = ((Number) createStaff(admin, "bulk-a@test.tw", "甲")[0]).longValue();
        long b = ((Number) createStaff(admin, "bulk-b@test.tw", "乙")[0]).longValue();
        String ids = "\"ids\":[%d,%d]".formatted(a, b);

        mvc.perform(json(post("/api/admin/staff/batch/memberships"), "{%s,\"unitId\":%d,\"roles\":[\"AUTHOR\",\"PROCTOR\"]}"
                .formatted(ids, unit)).session(admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/units/" + unit + "/members").session(admin)).andExpect(jsonPath("$.length()").value(4));

        var issued = mvc.perform(json(post("/api/admin/staff/batch/reset-password"), "{%s}".formatted(ids)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andReturn();
        login("bulk-b@test.tw", read(issued, "$[1].temporaryPassword"));

        // Including yourself undoes the whole batch
        var me = mvc.perform(get("/api/auth/me").session(admin)).andReturn();
        long self = ((Number) read(me, "$.id")).longValue();
        mvc.perform(json(post("/api/admin/staff/batch/active"), "{\"ids\":[%d,%d],\"active\":false}".formatted(a, self))
                .session(admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("self"));
        mvc.perform(get("/api/admin/staff").session(admin)).andExpect(jsonPath("$[?(@.id == %d)].active".formatted(a)).value(true));

        mvc.perform(json(post("/api/admin/staff/batch/active"), "{%s,\"active\":false}".formatted(ids)).session(admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/admin/staff").session(admin))
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(a)).value(false))
                .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(b)).value(false));
    }
}
