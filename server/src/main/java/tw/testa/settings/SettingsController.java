package tw.testa.settings;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tw.testa.ai.ModelClient;
import tw.testa.audit.AuditLogRepository;
import tw.testa.audit.AuditService;
import tw.testa.common.ApiException;
import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;

/**
 * System settings for the admin pages (system administrators only). Every change is audited.
 * The AI API key is write-only: it is stored, never sent back and never written to the audit log.
 */
@RestController
@RequestMapping("/api/admin/settings")
@PreAuthorize("@access.isSystemAdmin()")
public class SettingsController {
    private final SettingsService settings;
    private final ModelClient model;
    private final AuditService audit;
    private final AuditLogRepository auditLog;
    private final StaffRepository staff;

    public SettingsController(SettingsService settings, ModelClient model, AuditService audit, AuditLogRepository auditLog,
            StaffRepository staff) {
        this.settings = settings;
        this.auditLog = auditLog;
        this.staff = staff;
        this.model = model;
        this.audit = audit;
    }

    public record AiDto(boolean enabled, String baseUrl, String model, boolean apiKeySet) {}

    /** The numbers as they would be without any change in the admin pages */
    public record Defaults(int flagPercent, int retentionYears, int loginMaxFailures, int loginLockMinutes, int staffIdleMinutes,
            int aiTimeoutSeconds) {}

    /** The latest change, from the audit log: who, when and what (the audit text) */
    public record LastChange(Instant at, String by, String detail) {}

    public record SettingsDto(AiDto ai, int aiTimeoutSeconds, int flagPercent, int retentionYears, int loginMaxFailures,
            int loginLockMinutes, int staffIdleMinutes, Defaults defaults, LastChange lastChange) {}

    /** aiApiKey: null keeps the stored key, an empty string removes it. */
    public record SaveRequest(
            @NotNull Boolean aiEnabled,
            @NotNull @Size(max = 500, message = "AI 服務網址太長了。") String aiBaseUrl,
            @NotNull @Size(max = 200, message = "模型名稱太長了。") String aiModel,
            @Size(max = 500, message = "API 金鑰太長了。") String aiApiKey,
            @Min(value = 5, message = "評分差距提醒最少 5%。") @Max(value = 100, message = "評分差距提醒最多 100%。") int flagPercent,
            @Min(value = 1, message = "資料至少保存 1 年。") @Max(value = 10, message = "資料最多保存 10 年。") int retentionYears,
            @Min(value = 3, message = "至少要允許輸錯 3 次。") @Max(value = 20, message = "最多允許輸錯 20 次。") int loginMaxFailures,
            @Min(value = 1, message = "鎖定時間至少 1 分鐘。") @Max(value = 1440, message = "鎖定時間最多 1440 分鐘（一天）。") int loginLockMinutes,
            @Min(value = 5, message = "閒置登出至少 5 分鐘。") @Max(value = 1440, message = "閒置登出最多 1440 分鐘（一天）。") int staffIdleMinutes,
            @Min(value = 30, message = "AI 等待時間至少 30 秒。") @Max(value = 1800, message = "AI 等待時間最多 30 分鐘。") int aiTimeoutSeconds) {}

    /** Automatic backups. everyHours: 6, 12, 24 or 168; weekday (1 = Monday) only matters when weekly. */
    public record BackupRequest(boolean enabled, int everyHours,
            @Min(value = 0, message = "請選擇備份時間。") @Max(value = 23, message = "請選擇備份時間。") int hour,
            @Min(value = 1, message = "請選擇星期幾。") @Max(value = 7, message = "請選擇星期幾。") int weekday,
            @Min(value = 1, message = "備份至少保留 1 天。") @Max(value = 365, message = "備份最多保留 365 天。") int keepDays) {}

    /** apiKey: null tests with the stored key. */
    public record TestRequest(@NotNull @Size(max = 500) String baseUrl, @Size(max = 500) String apiKey) {}

    @GetMapping
    public SettingsDto get() {
        SettingsService.Ai ai = settings.ai();
        return new SettingsDto(new AiDto(ai.enabled(), ai.baseUrl(), ai.model(), !ai.apiKey().isBlank()), ai.timeoutSeconds(),
                settings.flagPercent(), settings.retentionYears(), settings.loginMaxFailures(), settings.loginLockMinutes(),
                settings.staffIdleMinutes(),
                new Defaults(settings.defaultNumber(SettingsService.FLAG_PERCENT), settings.defaultNumber(SettingsService.RETENTION_YEARS),
                        settings.defaultNumber(SettingsService.LOGIN_MAX_FAILURES), settings.defaultNumber(SettingsService.LOGIN_LOCK_MINUTES),
                        settings.defaultNumber(SettingsService.STAFF_IDLE_MINUTES), settings.defaultNumber(SettingsService.AI_TIMEOUT_SECONDS)),
                auditLog.findFirstByActionOrderByIdDesc("SETTINGS_CHANGED")
                        .map(a -> new LastChange(a.getAt(), by(a.getActorId(), a.getActorLabel()), a.getDetail())).orElse(null));
    }

    @PutMapping
    public SettingsDto save(@Valid @RequestBody SaveRequest body) {
        String url = body.aiBaseUrl().strip();
        if (!url.isEmpty()) checkUrl(url);
        if (body.aiEnabled() && url.isEmpty()) {
            throw ApiException.badRequest("ai_url_required", "要開啟 AI 匯入，請先填寫 AI 服務網址。");
        }

        SettingsService.Ai before = settings.ai();
        Map<String, String> changes = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        if (body.aiEnabled() != before.enabled()) {
            changes.put(SettingsService.AI_ENABLED, String.valueOf(body.aiEnabled()));
            notes.add((body.aiEnabled() ? "開啟" : "關閉") + " AI 匯入題目");
        }
        if (!url.equals(before.baseUrl())) {
            changes.put(SettingsService.AI_BASE_URL, url);
            notes.add("AI 服務網址改為 " + (url.isEmpty() ? "（空白）" : url));
        }
        String modelName = body.aiModel().strip();
        if (!modelName.equals(before.model())) {
            changes.put(SettingsService.AI_MODEL, modelName);
            notes.add("AI 模型改為 " + (modelName.isEmpty() ? "（服務預設）" : modelName));
        }
        if (body.aiApiKey() != null && !body.aiApiKey().strip().equals(before.apiKey())) {
            changes.put(SettingsService.AI_API_KEY, body.aiApiKey().strip());
            notes.add(body.aiApiKey().isBlank() ? "移除 AI API 金鑰" : "更新 AI API 金鑰");
        }
        number(changes, notes, SettingsService.FLAG_PERCENT, "評分差距提醒", settings.flagPercent(), body.flagPercent(), "%");
        number(changes, notes, SettingsService.RETENTION_YEARS, "資料保存年數", settings.retentionYears(), body.retentionYears(), " 年");
        number(changes, notes, SettingsService.LOGIN_MAX_FAILURES, "登入錯誤上限", settings.loginMaxFailures(), body.loginMaxFailures(), " 次");
        number(changes, notes, SettingsService.LOGIN_LOCK_MINUTES, "登入鎖定時間", settings.loginLockMinutes(), body.loginLockMinutes(), " 分鐘");
        number(changes, notes, SettingsService.STAFF_IDLE_MINUTES, "閒置自動登出", settings.staffIdleMinutes(), body.staffIdleMinutes(), " 分鐘");
        number(changes, notes, SettingsService.AI_TIMEOUT_SECONDS, "AI 等待時間", settings.aiTimeoutSeconds(), body.aiTimeoutSeconds(), " 秒");

        if (!changes.isEmpty()) {
            settings.save(changes);
            audit.record("SETTINGS_CHANGED", "SETTINGS", null, null, null, String.join("；", notes));
        }
        return get();
    }

    @PutMapping("/backup")
    public SettingsService.BackupSchedule saveBackup(@Valid @RequestBody BackupRequest body) {
        if (!SettingsService.BACKUP_INTERVALS.contains(body.everyHours())) {
            throw ApiException.badRequest("backup_interval_invalid", "請選擇多久備份一次。");
        }
        SettingsService.BackupSchedule before = settings.backupSchedule();
        SettingsService.BackupSchedule after = new SettingsService.BackupSchedule(body.enabled(), body.everyHours(), body.hour(),
                body.everyHours() == 168 ? body.weekday() : before.weekday(), body.keepDays());
        Map<String, String> changes = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        if (after.enabled() != before.enabled()) {
            changes.put(SettingsService.BACKUP_ENABLED, String.valueOf(after.enabled()));
            notes.add((after.enabled() ? "開啟" : "關閉") + "自動備份");
        }
        String was = describe(before), now = describe(after);
        if (!was.equals(now)) {
            changes.put(SettingsService.BACKUP_EVERY_HOURS, String.valueOf(after.everyHours()));
            changes.put(SettingsService.BACKUP_HOUR, String.valueOf(after.hour()));
            changes.put(SettingsService.BACKUP_WEEKDAY, String.valueOf(after.weekday()));
            notes.add("自動備份時間 " + was + " 改為 " + now);
        }
        number(changes, notes, SettingsService.BACKUP_KEEP_DAYS, "備份保留天數", before.keepDays(), after.keepDays(), " 天");
        if (!changes.isEmpty()) {
            settings.save(changes);
            audit.record("SETTINGS_CHANGED", "SETTINGS", null, null, null, String.join("；", notes));
        }
        return settings.backupSchedule();
    }

    /** "每天 02:00", "每週日 02:00", "每 6 小時（從 02:00 起）" */
    static String describe(SettingsService.BackupSchedule s) {
        String at = "%02d:00".formatted(s.hour());
        return switch (s.everyHours()) {
            case 24 -> "每天 " + at;
            case 168 -> "每週" + "一二三四五六日".charAt(s.weekday() - 1) + " " + at;
            default -> "每 %d 小時（從 %s 起）".formatted(s.everyHours(), at);
        };
    }

    /** Checks an endpoint before saving it, and lists its models so the administrator can pick one. */
    @PostMapping("/ai/test")
    public ModelClient.Probe test(@Valid @RequestBody TestRequest body) {
        String url = body.baseUrl().strip();
        if (url.isEmpty()) throw ApiException.badRequest("ai_url_required", "請先填寫 AI 服務網址。");
        checkUrl(url);
        return model.probe(url, body.apiKey() != null ? body.apiKey().strip() : settings.ai().apiKey());
    }

    private String by(Long staffId, String label) {
        return staffId == null ? label : staff.findById(staffId).map(Staff::getName).orElse(label);
    }

    private static void number(Map<String, String> changes, List<String> notes, String key, String label, int before, int after, String unit) {
        if (before == after) return;
        changes.put(key, String.valueOf(after));
        notes.add("%s %d%s 改為 %d%s".formatted(label, before, unit, after, unit));
    }

    private static void checkUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("ai_url_invalid", "AI 服務網址要以 http:// 或 https:// 開頭，例如 http://10.0.0.5:8090。");
        }
    }
}
