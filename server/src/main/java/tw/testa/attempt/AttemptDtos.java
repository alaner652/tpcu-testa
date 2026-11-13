package tw.testa.attempt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import tw.testa.exam.Exam;

/** 考生端 API 的資料格式。這裡的任何東西都不能包含答案。 */
public final class AttemptDtos {
    private AttemptDtos() {}

    public record LoginRequest(@NotBlank(message = "請輸入准考證號。") String ticket,
            @NotBlank(message = "請輸入登入碼。") String code) {}

    public record ExamInfo(String name, Instant startAt, int duration, Exam.Mode mode, String status,
            int questionCount, BigDecimal totalPoints) {}

    public record AttemptInfo(Instant startedAt, Instant deadlineAt, Instant submittedAt, long remainingSeconds) {}

    /** 考生登入後看到的全部：自己是誰、哪一場考試、作答到哪裡。 */
    public record State(String name, String ticket, String dept, int seat, ExamInfo exam, AttemptInfo attempt) {}

    public record PaperChoice(String id, String text) {}

    public record PaperBlank(String id) {}

    /** 去掉答案的題目。 */
    public record PaperQuestion(String id, String type, String stem, BigDecimal points, List<PaperChoice> choices,
            List<PaperBlank> blanks, Integer wordLimit) {}

    public record Paper(String examName, List<PaperQuestion> questions, Map<String, AnswerContent> answers,
            Instant deadlineAt, long remainingSeconds) {}

    public record Saved(long remainingSeconds) {}
}
