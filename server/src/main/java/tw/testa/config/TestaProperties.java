package tw.testa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("testa")
public record TestaProperties(@DefaultValue BootstrapAdmin bootstrapAdmin, @DefaultValue Login login,
        @DefaultValue("false") boolean devSeed, @DefaultValue Grading grading) {

    /** flagRatio：評分者之間最高分與最低分的差距超過「配分 × 這個比例」就標記給承辦人看。 */
    public record Grading(@DefaultValue("0.3") double flagRatio) {}

    public record BootstrapAdmin(@DefaultValue("") String email, @DefaultValue("") String password,
            @DefaultValue("系統管理員") String name) {}

    public record Login(@DefaultValue("5") int maxFailures, @DefaultValue("15") int lockMinutes) {}
}
