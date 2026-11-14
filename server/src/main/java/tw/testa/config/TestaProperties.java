package tw.testa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("testa")
public record TestaProperties(@DefaultValue Setup setup, @DefaultValue Login login,
        @DefaultValue("false") boolean devSeed, @DefaultValue Grading grading,
        @DefaultValue("") String backupDir, @DefaultValue Retention retention) {

    /** flagRatio: warn the officer when the highest and lowest grader differ by more than points x ratio (20 on a 100-point question). */
    public record Grading(@DefaultValue("0.2") double flagRatio) {}

    /** years: exam data is kept until the end of (year the exam ended + years); after that an archived exam can be deleted. */
    public record Retention(@DefaultValue("1") int years) {}

    /** token: optional fixed first-run setup token; when blank a random one is generated and logged at start-up. */
    public record Setup(@DefaultValue("") String token) {}

    public record Login(@DefaultValue("5") int maxFailures, @DefaultValue("15") int lockMinutes) {}
}
