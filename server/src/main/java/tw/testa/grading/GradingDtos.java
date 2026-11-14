package tw.testa.grading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class GradingDtos {
    private GradingDtos() {}

    // ── Graders ──
    /** An exam I am assigned to grade. */
    public record GradingExam(Long examId, String name, int total, int done) {}

    /** An essay answer to grade (or graded). Anonymous: a random number only, no candidate identity, no other scores. */
    public record Item(String anonId, String questionId, int number, String stem, String rubric, BigDecimal points,
            String answer, BigDecimal myScore, String myComment) {}

    public record ScoreRequest(@NotNull(message = "請輸入分數。") @DecimalMin(value = "0", message = "分數不能小於 0。") BigDecimal score,
            @Size(max = 2000, message = "評語太長了。") String comment) {}

    // ── Officers ──
    public record GraderInfo(Long staffId, String name, String email, boolean assigned, int done, int total) {}

    public record SetGradersRequest(@NotNull List<@NotNull Long> staffIds) {}

    /** Scores are visible only once locked (every grader has submitted). */
    public record ItemSummary(String anonId, String questionId, int number, BigDecimal points, int graded, int graders,
            boolean locked, List<BigDecimal> scores, BigDecimal average, boolean flagged) {}

    public record ResultRow(String ticket, String name, String dept, int seat, String status, BigDecimal autoScore,
            BigDecimal essayScore, BigDecimal total, int pending) {}

    /** complete: everyone has submitted (or was absent) and every essay is graded, so results can be published. */
    public record Results(BigDecimal fullMarks, int graders, int itemsTotal, int itemsLocked, boolean complete,
            Instant publishedAt, List<ResultRow> rows, List<ItemSummary> items) {}

    /** A candidate's own result (after publishing). All three scores are null for absentees. */
    public record CandidateResult(Instant publishedAt, BigDecimal fullMarks, BigDecimal autoScore, BigDecimal essayScore,
            BigDecimal total) {}

    public record ReopenRequest(@NotBlank String anonId, @NotBlank String questionId,
            @NotBlank(message = "請填寫重開評分的理由。") @Size(max = 500) String reason) {}
}
