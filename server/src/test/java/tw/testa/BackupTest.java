package tw.testa;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    static {
        try {
            DIR = Files.createTempDirectory("testa-backups");
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("testa.backup-dir", DIR::toString);
    }

    @Test
    void 系統管理員看得到備份清單_新的在前_只列備份檔() throws Exception {
        Path old = Files.write(DIR.resolve("testa-20261113-030000.sql.gz"), new byte[10]);
        Files.setLastModifiedTime(old, FileTime.from(Instant.parse("2026-11-12T19:00:00Z")));
        Path latest = Files.write(DIR.resolve("testa-20261114-030000.sql.gz"), new byte[25]);
        Files.setLastModifiedTime(latest, FileTime.from(Instant.parse("2026-11-13T19:00:00Z")));
        Files.write(DIR.resolve("testa-20261115-030000.sql.gz.tmp"), new byte[5]);   // 還沒寫完的不算
        Files.write(DIR.resolve("notes.txt"), new byte[5]);

        mvc.perform(get("/api/admin/backups").session(adminSession())).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("testa-20261114-030000.sql.gz"))
                .andExpect(jsonPath("$.items[0].size").value(25))
                .andExpect(jsonPath("$.items[0].at").value("2026-11-13T19:00:00Z"));
    }

    @Test
    void 不是系統管理員看不到() throws Exception {
        Object[] staff = createStaff(adminSession(), "backup-viewer@test.tw", "一般教職員");
        var session = login("backup-viewer@test.tw", (String) staff[1]);
        mvc.perform(get("/api/admin/backups").session(session)).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/admin/backups")).andExpect(status().isUnauthorized());
    }
}
