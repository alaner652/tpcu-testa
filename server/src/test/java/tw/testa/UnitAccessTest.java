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
}
