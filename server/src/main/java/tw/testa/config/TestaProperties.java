package tw.testa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("testa")
public record TestaProperties(@DefaultValue Login login,
        @DefaultValue("false") boolean devSeed, @DefaultValue Grading grading,
        @DefaultValue("") String backupDir, @DefaultValue("") String backupControlDir,
        @DefaultValue("30") int backupKeepDays, @DefaultValue Retention retention, @DefaultValue Ai ai) {

    /**
     * AI question import. baseUrl: an OpenAI-compatible endpoint (for example a local llama-server); blank turns the feature off.
     * Only exam text uploaded by an author is sent there, never candidate data.
     */
    public record Ai(@DefaultValue("") String baseUrl, @DefaultValue("") String model, @DefaultValue("") String apiKey,
            @DefaultValue("300") int timeoutSeconds) {}

    /** flagRatio: warn the officer when the highest and lowest grader differ by more than points x ratio (20 on a 100-point question). */
    public record Grading(@DefaultValue("0.2") double flagRatio) {}

    /** years: exam data is kept until the end of (year the exam ended + years); after that an archived exam can be deleted. */
    public record Retention(@DefaultValue("1") int years) {}

    public record Login(@DefaultValue("5") int maxFailures, @DefaultValue("15") int lockMinutes) {}
}
