package tw.testa.bank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 題庫 API 的資料格式。題目的欄位與前端 src/types/question.ts 一一對應。 */
public final class BankDtos {
    private BankDtos() {}

    private static final String ID = "^[A-Za-z0-9_-]{1,40}$";

    public record ChoiceDto(@NotNull @Pattern(regexp = ID, message = "選項代號格式不正確。") String id,
            @Size(max = 5000, message = "選項文字太長了。") String text) {
        public ChoiceDto {
            if (text == null) text = "";
        }
    }

    public record BlankDto(@NotNull @Pattern(regexp = ID, message = "空格代號格式不正確。") String id,
            @Size(max = 20, message = "每個空格最多 20 個可接受的答案。") List<@NotNull @Size(max = 500, message = "填空答案太長了。") String> answers) {
        public BlankDto {
            if (answers == null) answers = List.of();
        }
    }

    public record QuestionDto(
            @NotNull @Pattern(regexp = ID, message = "題目代號格式不正確。") String id,
            @NotNull @Pattern(regexp = "single|multiple|blank|essay", message = "不支援的題型。") String type,
            @Size(max = 20000, message = "題目文字太長了。") String stem,
            @NotNull @DecimalMin(value = "0", message = "配分不能小於 0。") @DecimalMax(value = "100", message = "配分不能超過 100。") BigDecimal points,
            @Size(max = 10000, message = "解析太長了。") String explanation,
            @Size(max = 26, message = "選項最多 26 個。") List<@Valid @NotNull ChoiceDto> choices,
            @Size(max = 26) List<@NotNull String> correct,
            @Size(max = 50, message = "空格最多 50 個。") List<@Valid @NotNull BlankDto> blanks,
            Boolean caseSensitive,
            @Size(max = 10000, message = "評分規準太長了。") String rubric,
            @Min(value = 1, message = "字數上限至少為 1。") @Max(value = 100000, message = "字數上限太大了。") Integer wordLimit,
            Boolean aiGrading,
            Boolean review) {
        public QuestionDto {
            if (stem == null) stem = "";
            if (explanation == null) explanation = "";
            if (choices == null) choices = List.of();
            if (correct == null) correct = List.of();
            if (blanks == null) blanks = List.of();
            if (rubric == null) rubric = "";
            if (caseSensitive == null) caseSensitive = false;
            if (aiGrading == null) aiGrading = false;
            if (review == null) review = false;
        }
    }

    public record BankDto(Long id, Long unitId, String unitName, String name, String subject, int duration,
            int version, boolean editable, Instant updatedAt, List<QuestionDto> questions) {}

    public record CreateBankRequest(@NotNull(message = "請選擇單位。") Long unitId,
            @Size(max = 200, message = "題庫名稱太長了。") String name,
            @Size(max = 50, message = "科目名稱太長了。") String subject,
            @Min(value = 1, message = "作答時間至少 1 分鐘。") @Max(value = 600, message = "作答時間最多 600 分鐘。") Integer duration,
            @Size(max = 500, message = "一份題庫最多 500 題。") List<@Valid @NotNull QuestionDto> questions) {}

    public record SaveBankRequest(@NotNull(message = "缺少版本編號。") Integer version,
            @NotNull @Size(max = 200, message = "題庫名稱太長了。") String name,
            @NotNull @Size(max = 50, message = "科目名稱太長了。") String subject,
            @Min(value = 1, message = "作答時間至少 1 分鐘。") @Max(value = 600, message = "作答時間最多 600 分鐘。") int duration,
            @NotNull @Size(max = 500, message = "一份題庫最多 500 題。") List<@Valid @NotNull QuestionDto> questions) {}
}
