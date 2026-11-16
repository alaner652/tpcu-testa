package tw.testa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class BackupTest extends IntegrationTestBase {
    static final Path DIR;
    static final Path CONTROL;

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
    }

    @Test
    void nonAdministratorsCannotSeeBackups() throws Exception {
        Object[] staff = createStaff(adminSession(), "backup-viewer@test.tw", "一般教職員");
        var session = login("backup-viewer@test.tw", (String) staff[1]);
        mvc.perform(get("/api/admin/backups").session(session)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/admin/backups/run").with(csrf()).session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/backups/testa-20261116-120000.sql.gz").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/backups")).andExpect(status().isUnauthorized());
    }
}
