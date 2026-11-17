package tw.testa.config;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tw.testa.audit.AuditController.AuditDto;
import tw.testa.audit.AuditLog;
import tw.testa.audit.AuditLogRepository;

/**
 * The first admin tab: is the system healthy, and is there anything an administrator should act on.
 * Counts only; no exam content or scores (administrators cannot see those).
 */
@RestController
@RequestMapping("/api/admin/overview")
@PreAuthorize("@access.isSystemAdmin()")
public class OverviewController {
    /** Sign-ins and answering are left out of "recent activity"; they are counted instead */
    private static final List<String> ROUTINE = List.of("LOGIN", "LOGOUT", "CANDIDATE_LOGIN", "CANDIDATE_LOGOUT",
            "ATTEMPT_STARTED", "ATTEMPT_SUBMITTED");

    private final JdbcTemplate jdbc;
    private final AuditLogRepository auditLog;
    private final ObjectProvider<BuildProperties> build;
    private final String backupDir;

    public OverviewController(JdbcTemplate jdbc, AuditLogRepository auditLog, ObjectProvider<BuildProperties> build,
            TestaProperties props) {
        this.jdbc = jdbc;
        this.auditLog = auditLog;
        this.build = build;
        this.backupDir = props.backupDir();
    }

    /**
     * online: people (staff or candidates) active in the last 15 minutes. answering: candidates with an open attempt.
     * lockedStaff and temporaryPasswords are active accounts that may need a hand.
     */
    public record Counts(long staff, long units, long lockedStaff, long temporaryPasswords, long liveExams, long upcomingExams,
            long answering, long online, long failedLogins24h) {}

    public record Disk(long free, long total) {}

    public record Overview(String version, Instant startedAt, long databaseBytes, Disk disk, Counts counts, List<AuditDto> recent) {}

    @GetMapping
    public Overview get() {
        BuildProperties info = build.getIfAvailable();
        Counts counts = new Counts(
                count("select count(*) from staff where active"),
                count("select count(*) from unit where active"),
                count("select count(*) from staff where active and locked_until > now()"),
                count("select count(*) from staff where active and must_change_password"),
                // Same as the exam list: live from the start until someone ends it
                count("select count(*) from exam where status = 'PUBLISHED' and archived_at is null and (start_at is null or start_at <= now())"),
                count("select count(*) from exam where status = 'PUBLISHED' and archived_at is null and start_at > now()"),
                count("select count(*) from attempt where submitted_at is null and deadline_at > now()"),
                count("select count(*) from spring_session where last_access_time > (extract(epoch from now()) - 900) * 1000"),
                count("select count(*) from audit_log where action in ('LOGIN_FAILED', 'CANDIDATE_LOGIN_FAILED')"
                        + " and at > now() - interval '24 hours'"));
        Specification<AuditLog> notable = (root, query, cb) -> cb.not(root.get("action").in(ROUTINE));
        List<AuditDto> recent = auditLog.findAll(notable, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "id")))
                .map(AuditDto::of).toList();
        return new Overview(info != null ? info.getVersion() : "dev",
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),
                count("select pg_database_size(current_database())"), disk(), counts, recent);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** The disk that holds the backups, which on a single VM is the system disk too */
    private Disk disk() {
        if (backupDir.isBlank() || !Files.isDirectory(Path.of(backupDir))) return null;
        try {
            FileStore store = Files.getFileStore(Path.of(backupDir));
            return new Disk(store.getUsableSpace(), store.getTotalSpace());
        } catch (IOException e) {
            return null;
        }
    }
}
