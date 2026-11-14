package tw.testa.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backup status for the admin pages: lists the files in the backup directory so administrators can see daily backups are running.
 * The backup container does the backups (deploy/backup.sh). No download, trigger or restore here: backups contain papers and personal data.
 */
@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("@access.isSystemAdmin()")
public class BackupController {
    private static final int MAX_ITEMS = 60;
    private final String dir;

    public BackupController(TestaProperties props) {
        this.dir = props.backupDir();
    }

    public record BackupFile(String name, long size, Instant at) {}

    /** configured: a backup directory is set and readable. */
    public record Backups(boolean configured, List<BackupFile> items) {}

    @GetMapping
    public Backups list() {
        if (dir.isBlank()) return new Backups(false, List.of());
        Path path = Path.of(dir);
        if (!Files.isDirectory(path)) return new Backups(false, List.of());
        try (Stream<Path> files = Files.list(path)) {
            List<BackupFile> items = files
                    .filter(f -> f.getFileName().toString().matches("testa-.*\\.sql\\.gz") && Files.isRegularFile(f))
                    .map(BackupController::describe)
                    .sorted(Comparator.comparing(BackupFile::at).reversed())
                    .limit(MAX_ITEMS)
                    .toList();
            return new Backups(true, items);
        } catch (IOException | java.io.UncheckedIOException e) {
            return new Backups(false, List.of());
        }
    }

    private static BackupFile describe(Path f) {
        try {
            return new BackupFile(f.getFileName().toString(), Files.size(f), Files.getLastModifiedTime(f).toInstant());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
