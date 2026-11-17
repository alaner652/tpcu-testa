package tw.testa.config;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import tw.testa.attempt.AttemptRepository;
import tw.testa.audit.AuditLogRepository;
import tw.testa.audit.AuditService;
import tw.testa.auth.StaffPrincipal;
import tw.testa.common.ApiException;
import tw.testa.settings.SettingsService;

/**
 * Backup status and administrator actions. The dump directory stays read-only to this process; a separate signal directory asks the
 * isolated backup container to create, delete or restore a dump.
 */
@RestController
@RequestMapping("/api/admin/backups")
@PreAuthorize("@access.isSystemAdmin()")
public class BackupController {
    private static final int MAX_ITEMS = 60;
    private final String dir;
    private final BackupMaintenanceService maintenance;
    private final SettingsService settings;
    private final AttemptRepository attempts;
    private final AuditService audit;
    private final AuditLogRepository auditLog;

    public BackupController(TestaProperties props, BackupMaintenanceService maintenance, SettingsService settings,
            AttemptRepository attempts, AuditService audit, AuditLogRepository auditLog) {
        this.attempts = attempts;
        this.auditLog = auditLog;
        this.dir = props.backupDir();
        this.maintenance = maintenance;
        this.settings = settings;
        this.audit = audit;
    }

    public record BackupFile(String name, long size, Instant at) {}

    /** One finished operation of the backup container: kind is auto, manual, delete or restore. */
    public record Run(Instant at, String kind, boolean ok, String name) {}

    /** Free and total bytes of the disk that holds the dumps. */
    public record Disk(long free, long total) {}

    /**
     * nextRunAt: the next daily backup, as scheduled by the backup container. history: newest first.
     * lastDownloadAt: a copy kept somewhere else is the only protection when the server's disk fails.
     */
    public record Backups(boolean configured, boolean triggerSupported, boolean running, boolean lastFailed,
            boolean maintenance, String operation, int keepDays, List<BackupFile> items, long totalSize, Disk disk,
            Instant nextRunAt, List<Run> history, Instant lastDownloadAt) {}

    public record RestoreRequest(String confirmation) {}

    @GetMapping
    public Backups list() {
        Path control = maintenance.controlPath();
        boolean triggerSupported = control != null;
        String operation = maintenance.status();
        boolean running = triggerSupported && (Files.exists(control.resolve("request"))
                || Files.exists(control.resolve("delete-request")) || Files.exists(control.resolve("restore-request"))
                || operation.equals("backup") || operation.equals("deleting") || operation.equals("restoring"));
        boolean lastFailed = triggerSupported && operation.equals("error");
        int keepDays = settings.backupKeepDays();
        Instant nextRunAt = triggerSupported ? nextRun(control) : null;
        List<Run> history = triggerSupported ? history(control) : List.of();
        Instant lastDownloadAt = auditLog.findFirstByActionOrderByIdDesc("BACKUP_DOWNLOADED").map(a -> a.getAt()).orElse(null);
        Path path = dir.isBlank() ? null : Path.of(dir);
        if (path == null || !Files.isDirectory(path)) return new Backups(false, triggerSupported, running, lastFailed,
                maintenance.active(), operation, keepDays, List.of(), 0, null, nextRunAt, history, lastDownloadAt);
        try (Stream<Path> files = Files.list(path)) {
            List<BackupFile> all = files
                    .filter(f -> f.getFileName().toString().matches("testa-.*\\.sql\\.gz") && Files.isRegularFile(f))
                    .map(BackupController::describe)
                    .sorted(Comparator.comparing(BackupFile::at).reversed())
                    .toList();
            long totalSize = all.stream().mapToLong(BackupFile::size).sum();
            return new Backups(true, triggerSupported, running, lastFailed, maintenance.active(), operation, keepDays,
                    all.stream().limit(MAX_ITEMS).toList(), totalSize, disk(path), nextRunAt, history, lastDownloadAt);
        } catch (IOException | java.io.UncheckedIOException e) {
            return new Backups(false, triggerSupported, running, lastFailed, maintenance.active(), operation, keepDays,
                    List.of(), 0, null, nextRunAt, history, lastDownloadAt);
        }
    }

    private static Disk disk(Path path) {
        try {
            FileStore store = Files.getFileStore(path);
            return new Disk(store.getUsableSpace(), store.getTotalSpace());
        } catch (IOException e) {
            return null;
        }
    }

    /** The backup container writes the epoch second of its next daily run. */
    private static Instant nextRun(Path control) {
        try {
            Path file = control.resolve("next-run");
            return Files.isRegularFile(file) ? Instant.ofEpochSecond(Long.parseLong(Files.readString(file).trim())) : null;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    /** Lines of "epoch-second, kind, ok|error, file name" separated by tabs, oldest first. */
    private static List<Run> history(Path control) {
        Path file = control.resolve("history");
        if (!Files.isRegularFile(file)) return List.of();
        try (Stream<String> lines = Files.lines(file)) {
            List<Run> runs = new ArrayList<>(lines.map(line -> line.split("\t", -1)).filter(f -> f.length >= 3).map(f -> {
                try {
                    return new Run(Instant.ofEpochSecond(Long.parseLong(f[0])), f[1], f[2].equals("ok"), f.length > 3 ? f[3] : "");
                } catch (NumberFormatException e) {
                    return null;
                }
            }).filter(Objects::nonNull).toList());
            Collections.reverse(runs);
            return runs.stream().limit(20).toList();
        } catch (IOException | java.io.UncheckedIOException e) {
            return List.of();
        }
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void run() {
        Path control = availableControl();
        ensureIdle(control);
        writeSignal(control, "request", Instant.now().toString());
        audit.record("BACKUP_REQUESTED", "backup", null);
    }

    @DeleteMapping("/{name:.+}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void delete(@PathVariable String name) {
        backupFile(name);
        Path control = availableControl();
        ensureIdle(control);
        writeSignal(control, "delete-request", name);
        audit.record("BACKUP_DELETE_REQUESTED", "backup", name);
    }

    @PostMapping("/{name:.+}/restore")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void restore(@PathVariable String name, @RequestBody RestoreRequest body) {
        backupFile(name);
        if (body == null || !"還原".equals(body.confirmation())) {
            throw ApiException.badRequest("restore_confirmation_required", "請輸入「還原」確認這個操作。");
        }
        Path control = availableControl();
        ensureIdle(control);
        // A restore would drop every answer saved after the dump was taken
        if (attempts.existsInProgress(Instant.now())) {
            throw ApiException.conflict("exam_in_progress", "有考生正在作答，考試結束前不能還原備份。");
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof StaffPrincipal staff)) throw ApiException.forbidden();
        String label = staff.email().replace("\r", "").replace("\n", "");
        writeSignal(control, "restore-request", name + "\n" + label + "\n");
        audit.record("BACKUP_RESTORE_REQUESTED", "backup", name);
    }

    @GetMapping("/{name:.+}")
    public ResponseEntity<Resource> download(@PathVariable String name) {
        Path file = backupFile(name);
        audit.record("BACKUP_DOWNLOADED", "backup", name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/gzip"))
                .contentLength(size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(file));
    }

    private Path availableControl() {
        Path control = maintenance.controlPath();
        if (control == null) {
            throw ApiException.conflict("backup_unavailable", "這台主機尚未啟用備份管理，請聯絡系統維護人員。");
        }
        return control;
    }

    private void ensureIdle(Path control) {
        String status = maintenance.status();
        if (maintenance.active() || Files.exists(control.resolve("request")) || Files.exists(control.resolve("delete-request"))
                || Files.exists(control.resolve("restore-request")) || status.equals("backup") || status.equals("deleting")
                || status.equals("restoring")) {
            throw ApiException.conflict("backup_running", "另一個備份操作正在執行，請稍候。");
        }
    }

    private static void writeSignal(Path control, String name, String content) {
        Path temp = control.resolve(name + ".tmp");
        try {
            Files.writeString(temp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(temp, control.resolve(name), StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            throw ApiException.conflict("backup_running", "另一個備份操作正在執行，請稍候。");
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
            throw ApiException.conflict("backup_unavailable", "目前無法啟動備份操作，請聯絡系統維護人員。");
        }
    }

    private Path backupFile(String name) {
        if (!name.matches("testa-[0-9]{8}-[0-9]{6}\\.sql\\.gz") || dir.isBlank()) {
            throw ApiException.notFound("找不到這份備份。");
        }
        Path root = Path.of(dir).normalize();
        Path file = root.resolve(name).normalize();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) throw ApiException.notFound("找不到這份備份。");
        return file;
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
