package tw.testa.attempt;

import java.util.List;
import java.util.Map;

/** 一題的作答：選擇題的選項代號、填空題每個空格的文字、申論題的全文。與前端 lib/grading.ts 的 Attempt 相同。 */
public record AnswerContent(List<String> choices, Map<String, String> blanks, String essay) {
    public AnswerContent {
        if (choices == null) choices = List.of();
        if (blanks == null) blanks = Map.of();
        if (essay == null) essay = "";
    }
}
