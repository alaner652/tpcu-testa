package tw.testa.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tw.testa.audit.AuditService;
import tw.testa.settings.SettingsService;
import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;

/** Shared state between the backend and the isolated backup container. */
@Service
public class BackupMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(BackupMaintenanceService.class);
    private final String controlDir;
    private final AuditService audit;
    private final SettingsService settings;
    private final StaffRepository staff;

    public BackupMaintenanceService(TestaProperties props, AuditService audit, SettingsService settings, StaffRepository staff) {
        this.controlDir = props.backupControlDir();
        this.audit = audit;
        this.settings = settings;
        this.staff = staff;
    }

    public Path controlPath() {
        if (controlDir.isBlank()) return null;
        Path path = Path.of(controlDir);
        return Files.isDirectory(path) && Files.isWritable(path) ? path : null;
    }

    public boolean active() {
        Path control = controlPath();
        return control != null && Files.isRegularFile(control.resolve("maintenance"));
    }

    public String status() {
        Path control = controlPath();
        if (control == null) return "";
        try {
            Path status = control.resolve("status");
            return Files.isRegularFile(status) ? Files.readString(status).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** The pre-restore audit entry is part of the old database. Record completion after the restored database is live. */
    @Scheduled(fixedDelay = 2000, initialDelay = 2000)
    public void recordCompletedRestore() {
        Path control = controlPath();
        if (control == null) return;
        Path completed = control.resolve("restore-completed");
        if (!Files.isRegularFile(completed)) return;
        try {
            List<String> lines = Files.readAllLines(completed);
            if (lines.size() < 2) {
                log.error("Invalid restore completion marker");
                Files.deleteIfExists(completed);
                return;
            }
            String name = lines.get(0);
            String actorLabel = lines.get(1);
            // The id came from the replaced database; in the restored one it may be missing or belong to someone else
            Long actorId = staff.findByEmail(actorLabel).map(Staff::getId).orElse(null);
            settings.reload();
            audit.recordAs("BACKUP_RESTORED", "STAFF", actorId, actorLabel, name);
            Files.deleteIfExists(completed);
            log.info("Restore audit recorded for {}", name);
        } catch (RuntimeException | IOException e) {
            // The connection pool can still be recovering. Leave the marker and retry on the next tick.
            log.warn("Could not record completed restore yet", e);
        }
    }
}
