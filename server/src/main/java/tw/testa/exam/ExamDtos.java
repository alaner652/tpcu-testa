package tw.testa.exam;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ExamDtos {
    private ExamDtos() {}

    /**
     * status: draft / scheduled / live / ended. canManage: whether I own the exam's unit.
     * retainUntil: how long data is kept (set once the exam ends); an archived exam can be deleted after it.
     */
    public record ExamDto(Long id, Long unitId, String unitName, String name, Long bankId, String bankName,
            Instant startAt, int duration, Exam.Mode mode, String status, int questionCount, BigDecimal totalPoints,
            long takers, long inProgress, long submitted, boolean canManage, Instant resultsPublishedAt,
            Instant archivedAt, Instant retainUntil) {}

    public record CreateExamRequest(
            @NotBlank(message = "請輸入考試名稱。") @Size(max = 200, message = "考試名稱太長了。") String name,
            @NotNull(message = "請選擇題庫。") Long bankId,
            Instant startAt,
            @NotNull(message = "請輸入作答時間。") @Min(value = 1, message = "作答時間至少 1 分鐘。") @Max(value = 600, message = "作答時間最多 600 分鐘。") Integer duration,
            Exam.Mode mode) {}

    /** Only the fields to change. clearStartAt=true clears the start time. */
    public record UpdateExamRequest(@Size(min = 1, max = 200, message = "請輸入考試名稱。") String name, Long bankId,
            Instant startAt, Boolean clearStartAt,
            @Min(value = 1, message = "作答時間至少 1 分鐘。") @Max(value = 600, message = "作答時間最多 600 分鐘。") Integer duration,
            Exam.Mode mode) {}

    public record CandidateDto(Long id, String ticket, String name, String dept, int seat, String code,
            Candidate.Status status, boolean locked, int leaveCount, Instant deadlineAt, int extraMinutes) {}

    public record ExtendRequest(
            @NotNull(message = "請輸入要補幾分鐘。") @Min(value = 1, message = "至少補 1 分鐘。") @Max(value = 60, message = "一次最多補 60 分鐘。") Integer minutes,
            @NotBlank(message = "請填寫補時的原因。") @Size(max = 200, message = "原因太長了。") String reason) {}

    public record RosterRow(@NotBlank(message = "缺少准考證號。") @Size(max = 40, message = "准考證號太長了。") String ticket,
            @NotBlank(message = "缺少姓名。") @Size(max = 100, message = "姓名太長了。") String name,
            @Size(max = 100, message = "科系名稱太長了。") String dept) {}

    public record RosterProblem(String row, String who, String reason) {}

    /** Result of checking an uploaded file: rows can be imported, problems need fixing (not imported). */
    public record RosterPreview(String file, List<RosterRow> rows, List<RosterProblem> problems) {}

    public record ImportRequest(
            @NotNull @Size(min = 1, max = RosterParser.MAX_ROWS, message = "一次最多匯入 5000 位考生。") List<@Valid @NotNull RosterRow> rows) {}

    public record ImportResult(int added, long total) {}

    public record CodeDto(String code) {}

    /** Several candidates at once. All succeed or none do. */
    public record CandidateIds(
            @NotEmpty(message = "請至少選擇一位考生。") @Size(max = 1000, message = "一次最多處理 1000 位考生。") List<@NotNull Long> ids) {}

    public record BatchExtendRequest(
            @NotEmpty(message = "請至少選擇一位考生。") @Size(max = 1000, message = "一次最多處理 1000 位考生。") List<@NotNull Long> ids,
            @NotNull(message = "請輸入要補幾分鐘。") @Min(value = 1, message = "至少補 1 分鐘。") @Max(value = 60, message = "一次最多補 60 分鐘。") Integer minutes,
            @NotBlank(message = "請填寫補時的原因。") @Size(max = 200, message = "原因太長了。") String reason) {}

    public record DeleteRequest(@Size(max = 500) String reason) {}
}
