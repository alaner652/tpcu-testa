package tw.testa.attempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import tw.testa.exam.Exam;

/** Candidate API payloads. Nothing here may contain answers. */
public final class AttemptDtos {
    private AttemptDtos() {}

    public record LoginRequest(@NotBlank(message = "請輸入准考證號。") String ticket,
            @NotBlank(message = "請輸入登入碼。") String code) {}

    /** candidateResults: whether the candidate will be able to see the score here once it is published. */
    public record ExamInfo(String name, Instant startAt, int duration, Exam.Mode mode, String status,
            int questionCount, BigDecimal totalPoints, boolean candidateResults) {}

    public record AttemptInfo(Instant startedAt, Instant deadlineAt, Instant submittedAt, long remainingSeconds) {}

    /** The candidate's own result, only after results are published; total is null for absentees. Scores only, no answers or comments. */
    public record Result(Instant publishedAt, BigDecimal fullMarks, BigDecimal autoScore, BigDecimal essayScore,
            BigDecimal total) {}

    /** Everything a signed-in candidate sees: who they are, which exam, progress and (once published) the result. */
    public record State(String name, String ticket, String dept, int seat, ExamInfo exam, AttemptInfo attempt,
            Result result) {}

    public record PaperChoice(String id, String text) {}

    public record PaperBlank(String id) {}

    /** A question without its answers. */
    public record PaperQuestion(String id, String type, String stem, BigDecimal points, List<PaperChoice> choices,
            List<PaperBlank> blanks, Integer wordLimit) {}

    public record Paper(String examName, List<PaperQuestion> questions, Map<String, AnswerContent> answers,
            Instant deadlineAt, long remainingSeconds) {}

    public record Saved(long remainingSeconds) {}
}
