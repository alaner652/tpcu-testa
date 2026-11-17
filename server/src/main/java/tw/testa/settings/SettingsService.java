package tw.testa.settings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

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

    /** ready: switched on and an endpoint is set. */
    public record Ai(boolean enabled, String baseUrl, String model, String apiKey, int timeoutSeconds) {
        public boolean ready() {
            return enabled && !baseUrl.isBlank();
        }
    }

    private final JdbcTemplate jdbc;
    private final Map<String, String> defaults = new HashMap<>();
    private final int aiTimeoutSeconds;
    private final String backupControlDir;
    private volatile Map<String, String> stored;

    public SettingsService(JdbcTemplate jdbc, TestaProperties props) {
        this.jdbc = jdbc;
        this.aiTimeoutSeconds = props.ai().timeoutSeconds();
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
    }

    public Ai ai() {
        return new Ai(Boolean.parseBoolean(get(AI_ENABLED)), get(AI_BASE_URL), get(AI_MODEL), get(AI_API_KEY), aiTimeoutSeconds);
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

    public int backupKeepDays() {
        int days = number(BACKUP_KEEP_DAYS);
        syncBackupKeepDays(days);
        return days;
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
        if (changes.containsKey(BACKUP_KEEP_DAYS)) syncBackupKeepDays(Integer.parseInt(changes.get(BACKUP_KEEP_DAYS)));
    }

    /** Drops the cache; the next read loads the table again. */
    public void reload() {
        stored = null;
    }

    private void syncBackupKeepDays(int days) {
        if (backupControlDir.isBlank()) return;
        Path control = Path.of(backupControlDir);
        if (!Files.isDirectory(control) || !Files.isWritable(control)) return;
        Path temp = control.resolve("keep-days.tmp");
        try {
            Files.writeString(temp, String.valueOf(days));
            Files.move(temp, control.resolve("keep-days"), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }
}
