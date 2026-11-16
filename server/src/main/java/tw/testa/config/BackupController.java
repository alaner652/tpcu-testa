package tw.testa.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tw.testa.audit.AuditService;
import tw.testa.common.ApiException;

/**
 * Backup status and safe administrator actions. The dump directory is read-only to this process; a separate signal directory asks the
 * backup container to run pg_dump. Restoring remains a host-only operation because it replaces the live database.
 */
@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("@access.isSystemAdmin()")
public class BackupController {
    private static final int MAX_ITEMS = 60;
    private final String dir;
    private final String controlDir;
    private final int keepDays;
    private final AuditService audit;

    public BackupController(TestaProperties props, AuditService audit) {
        this.dir = props.backupDir();
        this.controlDir = props.backupControlDir();
        this.keepDays = props.backupKeepDays();
        this.audit = audit;
    }

    public record BackupFile(String name, long size, Instant at) {}

    public record Backups(boolean configured, boolean triggerSupported, boolean running, boolean lastFailed,
            int keepDays, List<BackupFile> items) {}

    @GetMapping
    public Backups list() {
        boolean triggerSupported = controlPath() != null;
        boolean running = triggerSupported && (Files.exists(controlPath().resolve("request")) || status().equals("running"));
        boolean lastFailed = triggerSupported && status().equals("error");
        if (dir.isBlank()) return new Backups(false, triggerSupported, running, lastFailed, keepDays, List.of());
        Path path = Path.of(dir);
        if (!Files.isDirectory(path)) return new Backups(false, triggerSupported, running, lastFailed, keepDays, List.of());
        try (Stream<Path> files = Files.list(path)) {
            List<BackupFile> items = files
                    .filter(f -> f.getFileName().toString().matches("testa-.*\\.sql\\.gz") && Files.isRegularFile(f))
                    .map(BackupController::describe)
                    .sorted(Comparator.comparing(BackupFile::at).reversed())
                    .limit(MAX_ITEMS)
                    .toList();
            return new Backups(true, triggerSupported, running, lastFailed, keepDays, items);
        } catch (IOException | java.io.UncheckedIOException e) {
            return new Backups(false, triggerSupported, running, lastFailed, keepDays, List.of());
        }
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void run() {
        Path control = controlPath();
        if (control == null) {
            throw ApiException.conflict("backup_unavailable", "這台主機尚未啟用手動備份，請聯絡系統維護人員。");
        }
        if (Files.exists(control.resolve("request")) || status().equals("running")) {
            throw ApiException.conflict("backup_running", "備份正在建立中，請稍候。");
        }
        try {
            Files.writeString(control.resolve("request"), Instant.now().toString(), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw ApiException.conflict("backup_running", "備份正在建立中，請稍候。");
        } catch (IOException e) {
            throw ApiException.conflict("backup_unavailable", "目前無法啟動備份，請聯絡系統維護人員。");
        }
        audit.record("BACKUP_REQUESTED", "backup", null);
    }

    @GetMapping("/{name:.+}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        if (!name.matches("testa-[0-9]{8}-[0-9]{6}\\.sql\\.gz") || dir.isBlank()) {
            throw ApiException.notFound("找不到這份備份。");
        }
        Path file = Path.of(dir).resolve(name).normalize();
        if (!file.getParent().equals(Path.of(dir).normalize()) || !Files.isRegularFile(file)) {
            throw ApiException.notFound("找不到這份備份。");
        }
        audit.record("BACKUP_DOWNLOADED", "backup", name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/gzip"))
                .contentLength(size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(file));
    }

    private Path controlPath() {
        if (controlDir.isBlank()) return null;
        Path path = Path.of(controlDir);
        return Files.isDirectory(path) && Files.isWritable(path) ? path : null;
    }

    private String status() {
        Path control = controlPath();
        if (control == null) return "";
        try {
            Path status = control.resolve("status");
            return Files.isRegularFile(status) ? Files.readString(status).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static BackupFile describe(Path f) {
        try {
            return new BackupFile(f.getFileName().toString(), Files.size(f), Files.getLastModifiedTime(f).toInstant());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw ApiException.notFound("找不到這份備份。");
        }
    }
}
