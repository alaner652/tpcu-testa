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

    // ── 評分者 ──
    /** 我被指派評分的一場考試。 */
    public record GradingExam(Long examId, String name, int total, int done) {}

    /** 一個待評（或已評）的申論答案。匿名：只有隨機編號，沒有考生身分，也沒有別人的分數。 */
    public record Item(String anonId, String questionId, int number, String stem, String rubric, BigDecimal points,
            String answer, BigDecimal myScore, String myComment) {}

    public record ScoreRequest(@NotNull(message = "請輸入分數。") @DecimalMin(value = "0", message = "分數不能小於 0。") BigDecimal score,
            @Size(max = 2000, message = "評語太長了。") String comment) {}

    // ── 承辦人 ──
    public record GraderInfo(Long staffId, String name, String email, boolean assigned, int done, int total) {}

    public record SetGradersRequest(@NotNull List<@NotNull Long> staffIds) {}

    /** 鎖定（所有評分者都送出）後才看得到分數。 */
    public record ItemSummary(String anonId, String questionId, int number, BigDecimal points, int graded, int graders,
            boolean locked, List<BigDecimal> scores, BigDecimal average, boolean flagged) {}

    public record ResultRow(String ticket, String name, String dept, int seat, String status, BigDecimal autoScore,
            BigDecimal essayScore, BigDecimal total, int pending) {}

    /** complete：所有人都交卷（或沒來考）而且所有申論答案都評完，可以公布。 */
    public record Results(BigDecimal fullMarks, int graders, int itemsTotal, int itemsLocked, boolean complete,
            Instant publishedAt, List<ResultRow> rows, List<ItemSummary> items) {}

    public record ReopenRequest(@NotBlank String anonId, @NotBlank String questionId,
            @NotBlank(message = "請填寫重開評分的理由。") @Size(max = 500) String reason) {}
}
