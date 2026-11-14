package tw.testa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("testa")
public record TestaProperties(@DefaultValue BootstrapAdmin bootstrapAdmin, @DefaultValue Login login,
        @DefaultValue("false") boolean devSeed, @DefaultValue Grading grading,
        @DefaultValue("") String backupDir, @DefaultValue Retention retention) {

    /** flagRatio：評分者之間最高分與最低分的差距超過「配分 × 這個比例」就警示承辦人（100 分的題目差 20 分以上）。 */
    public record Grading(@DefaultValue("0.2") double flagRatio) {}

    /** years：考試資料保留到「考試結束那一年 + years」的年底，之後封存的考試才能刪除。 */
    public record Retention(@DefaultValue("1") int years) {}

    public record BootstrapAdmin(@DefaultValue("") String email, @DefaultValue("") String password,
            @DefaultValue("系統管理員") String name) {}

    public record Login(@DefaultValue("5") int maxFailures, @DefaultValue("15") int lockMinutes) {}
}
