package tw.testa.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.config.TestaProperties;

/**
 * Settings an administrator can change in the admin pages without touching the server.
 * Each one defaults to application.yml (environment variables); a row in app_setting overrides it.
 * Values are cached in memory: there is one backend, and every change goes through {@link #save}.
 */
@Service
public class SettingsService {
    static final String AI_ENABLED = "ai.enabled";
    static final String AI_BASE_URL = "ai.base-url";
    static final String AI_MODEL = "ai.model";
    static final String AI_API_KEY = "ai.api-key";
    static final String FLAG_PERCENT = "grading.flag-percent";
    static final String RETENTION_YEARS = "retention.years";
    static final String LOGIN_MAX_FAILURES = "login.max-failures";
    static final String LOGIN_LOCK_MINUTES = "login.lock-minutes";
    static final String BACKUP_KEEP_DAYS = "backup.keep-days";
    static final String BACKUP_ENABLED = "backup.enabled";
    static final String BACKUP_EVERY_HOURS = "backup.every-hours";
    static final String BACKUP_HOUR = "backup.hour";
    static final String BACKUP_WEEKDAY = "backup.weekday";
    static final String STAFF_IDLE_MINUTES = "login.idle-minutes";
    static final String AI_TIMEOUT_SECONDS = "ai.timeout-seconds";

    /** How often the backup container may run: every 6 or 12 hours, daily, or weekly. */
    public static final Set<Integer> BACKUP_INTERVALS = Set.of(6, 12, 24, 168);

    /**
     * Automatic backups: every everyHours, starting at hour:00 (on weekday, 1 = Monday, when weekly), server time.
     * keepDays: older dumps are removed after each backup.
     */
    public record BackupSchedule(boolean enabled, int everyHours, int hour, int weekday, int keepDays) {}

    /** ready: switched on and an endpoint is set. */
    public record Ai(boolean enabled, String baseUrl, String model, String apiKey, int timeoutSeconds) {
        public boolean ready() {
            return enabled && !baseUrl.isBlank();
        }
    }

    private final JdbcTemplate jdbc;
    private final Map<String, String> defaults = new HashMap<>();
    private final String backupControlDir;
    private volatile Map<String, String> stored;

    public SettingsService(JdbcTemplate jdbc, TestaProperties props) {
        this.jdbc = jdbc;
        this.backupControlDir = props.backupControlDir();
        // An endpoint set in the environment keeps working without visiting the admin pages
        defaults.put(AI_ENABLED, String.valueOf(!props.ai().baseUrl().isBlank()));
        defaults.put(AI_BASE_URL, props.ai().baseUrl().strip());
        defaults.put(AI_MODEL, props.ai().model().strip());
        defaults.put(AI_API_KEY, props.ai().apiKey().strip());
        defaults.put(FLAG_PERCENT, String.valueOf(Math.round(props.grading().flagRatio() * 100)));
        defaults.put(RETENTION_YEARS, String.valueOf(props.retention().years()));
        defaults.put(LOGIN_MAX_FAILURES, String.valueOf(props.login().maxFailures()));
        defaults.put(LOGIN_LOCK_MINUTES, String.valueOf(props.login().lockMinutes()));
        defaults.put(BACKUP_KEEP_DAYS, String.valueOf(props.backupKeepDays()));
        defaults.put(BACKUP_ENABLED, "true");
        defaults.put(BACKUP_EVERY_HOURS, "24");
        defaults.put(BACKUP_HOUR, "2");
        defaults.put(BACKUP_WEEKDAY, "7");
        defaults.put(STAFF_IDLE_MINUTES, String.valueOf(props.login().idleMinutes()));
        defaults.put(AI_TIMEOUT_SECONDS, String.valueOf(props.ai().timeoutSeconds()));
    }

    public Ai ai() {
        return new Ai(Boolean.parseBoolean(get(AI_ENABLED)), get(AI_BASE_URL), get(AI_MODEL), get(AI_API_KEY), number(AI_TIMEOUT_SECONDS));
    }

    /** Warn the officer when graders differ by more than this share of a question's points. */
    public double flagRatio() {
        return number(FLAG_PERCENT) / 100.0;
    }

    public int flagPercent() {
        return number(FLAG_PERCENT);
    }

    public int retentionYears() {
        return number(RETENTION_YEARS);
    }

    public int loginMaxFailures() {
        return number(LOGIN_MAX_FAILURES);
    }

    public int loginLockMinutes() {
        return number(LOGIN_LOCK_MINUTES);
    }

    public int staffIdleMinutes() {
        return number(STAFF_IDLE_MINUTES);
    }

    public int aiTimeoutSeconds() {
        return number(AI_TIMEOUT_SECONDS);
    }

    public int backupKeepDays() {
        return backupSchedule().keepDays();
    }

    /** Also hands the schedule to the backup container, which may have started before the backend. */
    public BackupSchedule backupSchedule() {
        int every = number(BACKUP_EVERY_HOURS);
        BackupSchedule schedule = new BackupSchedule(Boolean.parseBoolean(get(BACKUP_ENABLED)),
                BACKUP_INTERVALS.contains(every) ? every : 24, Math.floorMod(number(BACKUP_HOUR), 24),
                Math.clamp(number(BACKUP_WEEKDAY), 1, 7), number(BACKUP_KEEP_DAYS));
        syncBackupControl(schedule);
        return schedule;
    }

    @EventListener(ApplicationReadyEvent.class)
    void syncOnStart() {
        backupSchedule();
    }

    /** The value from application.yml, used when nothing is stored */
    int defaultNumber(String key) {
        return Integer.parseInt(defaults.get(key));
    }

    String get(String key) {
        String value = values().get(key);
        return value != null ? value : defaults.get(key);
    }

    private int number(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            return Integer.parseInt(defaults.get(key));
        }
    }

    private Map<String, String> values() {
        Map<String, String> current = stored;
        if (current == null) {
            Map<String, String> loaded = new HashMap<>();
            jdbc.query("select key, value from app_setting", rs -> {
                loaded.put(rs.getString(1), rs.getString(2));
            });
            stored = current = loaded;
        }
        return current;
    }

    /** A value equal to the default is not stored, so a later change to the default still applies. */
    @Transactional
    void save(Map<String, String> changes) {
        changes.forEach((key, value) -> {
            if (value.equals(defaults.get(key))) {
                jdbc.update("delete from app_setting where key = ?", key);
            } else {
                jdbc.update("insert into app_setting (key, value) values (?, ?) "
                        + "on conflict (key) do update set value = excluded.value, updated_at = now()", key, value);
            }
        });
        stored = null;
        if (changes.keySet().stream().anyMatch(key -> key.startsWith("backup."))) backupSchedule();
    }

    /** Drops the cache; the next read loads the table again. */
    public void reload() {
        stored = null;
    }

    /**
     * The backup container reads two small files: keep-days, and schedule as "enabled seconds anchor", where anchor is the
     * epoch second of one past slot. The container only adds whole intervals to it, so it needs no time zone.
     */
    private void syncBackupControl(BackupSchedule schedule) {
        if (backupControlDir.isBlank()) return;
        Path control = Path.of(backupControlDir);
        if (!Files.isDirectory(control) || !Files.isWritable(control)) return;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime slot = now.toLocalDate().atTime(schedule.hour(), 0).atZone(now.getZone());
        if (schedule.everyHours() == 168) slot = slot.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(schedule.weekday())));
        if (slot.isAfter(now)) slot = slot.minusDays(schedule.everyHours() == 168 ? 7 : 1);
        write(control, "keep-days", String.valueOf(schedule.keepDays()));
        write(control, "schedule", "%d %d %d".formatted(schedule.enabled() ? 1 : 0, schedule.everyHours() * 3600, slot.toEpochSecond()));
    }

    /** Skips an unchanged file, so the container does not see a change on every page load */
    private static void write(Path control, String name, String content) {
        Path target = control.resolve(name);
        Path temp = control.resolve(name + ".tmp");
        try {
            if (Files.isRegularFile(target) && Files.readString(target).trim().equals(content)) return;
            Files.writeString(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }
}
