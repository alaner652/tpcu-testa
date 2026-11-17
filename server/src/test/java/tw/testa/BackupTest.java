package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tw.testa.config.BackupMaintenanceService;

class BackupTest extends IntegrationTestBase {
    static final Path DIR;
    static final Path CONTROL;

    @Autowired
    BackupMaintenanceService maintenance;

    @Autowired
    JdbcTemplate jdbc;

    static {
        try {
            DIR = Files.createTempDirectory("testa-backups");
            CONTROL = Files.createTempDirectory("testa-backup-control");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("testa.backup-dir", DIR::toString);
        registry.add("testa.backup-control-dir", CONTROL::toString);
        registry.add("testa.backup-keep-days", () -> "30");
    }

    @Test
    void administratorSeesBackups_newestFirst_backupFilesOnly() throws Exception {
        try (var files = Files.list(DIR)) {
            files.forEach(f -> {
                try { Files.delete(f); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            });
        }
        Path old = Files.write(DIR.resolve("testa-20261113-030000.sql.gz"), new byte[10]);
        Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2026-11-12T19:00:00Z")));
        Path latest = Files.write(DIR.resolve("testa-20261114-030000.sql.gz"), new byte[25]);
        Files.setLastModifiedTime(latest, FileTime.from(Instant.parse("2026-11-13T19:00:00Z")));
        Files.write(DIR.resolve("testa-20261115-030000.sql.gz.tmp"), new byte[5]);   // still being written, not listed
        Files.write(DIR.resolve("notes.txt"), new byte[5]);

        mvc.perform(get("/api/admin/backups").session(adminSession())).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.triggerSupported").value(true))
                .andExpect(jsonPath("$.keepDays").value(30))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("testa-20261114-030000.sql.gz"))
                .andExpect(jsonPath("$.items[0].size").value(25))
                .andExpect(jsonPath("$.items[0].at").value("2026-11-13T19:00:00Z"));
    }

    @Test
    void administratorCanRequestAndDownloadBackup() throws Exception {
        Files.deleteIfExists(CONTROL.resolve("request"));
        Files.deleteIfExists(CONTROL.resolve("delete-request"));
        Files.deleteIfExists(CONTROL.resolve("restore-request"));
        Files.deleteIfExists(CONTROL.resolve("status"));
        var admin = adminSession();

        mvc.perform(post("/api/admin/backups/run").with(csrf()).session(admin))
                .andExpect(status().isAccepted());
        assertThat(Files.isRegularFile(CONTROL.resolve("request"))).isTrue();
        mvc.perform(post("/api/admin/backups/run").with(csrf()).session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("backup_running"));

        byte[] dump = { 1, 2, 3, 4 };
        String name = "testa-20261116-120000.sql.gz";
        Files.write(DIR.resolve(name), dump);
        mvc.perform(get("/api/admin/backups/" + name).session(admin))
                .andExpect(status().isOk()).andExpect(content().bytes(dump))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + name + "\""))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/audit-logs").param("action", "BACKUP_DOWNLOADED").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].targetId").value(name));
        mvc.perform(get("/api/admin/backups").session(admin)).andExpect(jsonPath("$.lastDownloadAt").isNotEmpty());
    }

    @Test
    void statusShowsScheduleHistoryAndDisk() throws Exception {
        Files.writeString(CONTROL.resolve("next-run"), "1795000000\n");
        Files.writeString(CONTROL.resolve("history"), """
                1794900000\tauto\tok\ttesta-20261117-030000.sql.gz
                not a line
                1794910000\tmanual\terror\t
                1794920000\tdelete\tok\ttesta-20261117-030000.sql.gz
                """);
        try {
            mvc.perform(get("/api/admin/backups").session(adminSession())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextRunAt").value(Instant.ofEpochSecond(1795000000).toString()))
                    .andExpect(jsonPath("$.history.length()").value(3))
                    .andExpect(jsonPath("$.history[0].kind").value("delete"))
                    .andExpect(jsonPath("$.history[1].kind").value("manual"))
                    .andExpect(jsonPath("$.history[1].ok").value(false))
                    .andExpect(jsonPath("$.history[2].name").value("testa-20261117-030000.sql.gz"))
                    .andExpect(jsonPath("$.disk.total").isNumber())
                    .andExpect(jsonPath("$.totalSize").isNumber());
        } finally {
            Files.deleteIfExists(CONTROL.resolve("next-run"));
            Files.deleteIfExists(CONTROL.resolve("history"));
        }
    }

    @Test
    void administratorCanDeleteAndRestoreWithConfirmation() throws Exception {
        Files.deleteIfExists(CONTROL.resolve("request"));
        Files.deleteIfExists(CONTROL.resolve("delete-request"));
        Files.deleteIfExists(CONTROL.resolve("restore-request"));
        Files.deleteIfExists(CONTROL.resolve("restore-completed"));
        Files.deleteIfExists(CONTROL.resolve("maintenance"));
        Files.writeString(CONTROL.resolve("status"), "ok");
        String name = "testa-20261116-130000.sql.gz";
        Files.write(DIR.resolve(name), new byte[] { 1, 2, 3 });
        var admin = adminSession();

        mvc.perform(delete("/api/admin/backups/" + name).with(csrf()).session(admin))
                .andExpect(status().isAccepted());
        assertThat(Files.readString(CONTROL.resolve("delete-request"))).isEqualTo(name);
        Files.delete(CONTROL.resolve("delete-request"));

        mvc.perform(json(post("/api/admin/backups/" + name + "/restore"), "{\"confirmation\":\"錯誤\"}").session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("restore_confirmation_required"));
        startAttempt(admin);
        mvc.perform(json(post("/api/admin/backups/" + name + "/restore"), "{\"confirmation\":\"還原\"}").session(admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("exam_in_progress"));
        assertThat(Files.exists(CONTROL.resolve("restore-request"))).isFalse();
        // Other test classes share the database and also leave attempts open
        jdbc.update("update attempt set submitted_at = now() where submitted_at is null");
        mvc.perform(json(post("/api/admin/backups/" + name + "/restore"), "{\"confirmation\":\"還原\"}").session(admin))
                .andExpect(status().isAccepted());
        assertThat(Files.readAllLines(CONTROL.resolve("restore-request")).get(0)).isEqualTo(name);

        Files.move(CONTROL.resolve("restore-request"), CONTROL.resolve("restore-completed"));
        maintenance.recordCompletedRestore();
        assertThat(Files.exists(CONTROL.resolve("restore-completed"))).isFalse();
        mvc.perform(get("/api/admin/audit-logs").param("action", "BACKUP_RESTORED").session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].detail").value(name));
    }

    /** A candidate who has started a live exam and not submitted. */
    private void startAttempt(MockHttpSession admin) throws Exception {
        long unit = createUnit(admin, "還原測試單位");
        Object[] staff = createStaff(admin, "restore-owner@test.tw", "負責人");
        grant(admin, unit, "restore-owner@test.tw", "OWNER");
        grant(admin, unit, "restore-owner@test.tw", "AUTHOR");
        var owner = login("restore-owner@test.tw", (String) staff[1]);
        var bank = mvc.perform(json(post("/api/banks"), ("{\"unitId\":%d,\"name\":\"題庫\",\"questions\":[{\"id\":\"q1\",\"type\":\"single\","
                + "\"stem\":\"1+1=?\",\"points\":5,\"choices\":[{\"id\":\"a\",\"text\":\"1\"},{\"id\":\"b\",\"text\":\"2\"}],"
                + "\"correct\":[\"b\"]}]}").formatted(unit)).session(owner)).andExpect(status().isCreated()).andReturn();
        var exam = mvc.perform(json(post("/api/exams"), "{\"name\":\"還原測試\",\"bankId\":%d,\"startAt\":\"%s\",\"duration\":30}"
                .formatted(((Number) read(bank, "$.id")).longValue(), Instant.now().minusSeconds(5))).session(owner))
                .andExpect(status().isCreated()).andReturn();
        long examId = ((Number) read(exam, "$.id")).longValue();
        mvc.perform(json(post("/api/exams/" + examId + "/candidates"), "{\"rows\":[{\"ticket\":\"R-0001\",\"name\":\"王小明\"}]}")
                .session(owner)).andExpect(status().isCreated());
        String code = read(mvc.perform(get("/api/exams/" + examId + "/candidates").session(owner)).andReturn(), "$[0].code");
        mvc.perform(post("/api/exams/" + examId + "/publish").with(csrf()).session(owner)).andExpect(status().isOk());
        var candidate = new MockHttpSession();
        mvc.perform(json(post("/api/candidate/login"), "{\"ticket\":\"R-0001\",\"code\":\"%s\"}".formatted(code)).session(candidate))
                .andExpect(status().isOk());
        mvc.perform(post("/api/candidate/start").with(csrf()).session(candidate)).andExpect(status().isOk());
    }

    @Test
    void maintenanceBlocksDatabaseRequestsButExposesItsStatus() throws Exception {
        var admin = adminSession();
        Files.writeString(CONTROL.resolve("status"), "restoring");
        Files.writeString(CONTROL.resolve("maintenance"), "");
        try {
            mvc.perform(get("/api/maintenance")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.status").value("restoring"));
            mvc.perform(get("/api/admin/backups").session(admin)).andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("maintenance"));
        } finally {
            Files.deleteIfExists(CONTROL.resolve("maintenance"));
            Files.writeString(CONTROL.resolve("status"), "ok");
        }
    }

    @Test
    void nonAdministratorsCannotSeeBackups() throws Exception {
        Object[] staff = createStaff(adminSession(), "backup-viewer@test.tw", "一般教職員");
        var session = login("backup-viewer@test.tw", (String) staff[1]);
        mvc.perform(get("/api/admin/backups").session(session)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/admin/backups/run").with(csrf()).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/backups/testa-20261116-120000.sql.gz").with(csrf()).session(session)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/admin/backups/testa-20261116-120000.sql.gz/restore"), "{\"confirmation\":\"還原\"}")
                .session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/backups/testa-20261116-120000.sql.gz").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/backups")).andExpect(status().isUnauthorized());
    }
}
