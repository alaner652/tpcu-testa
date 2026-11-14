package tw.testa.attempt;

import java.util.List;
import java.util.Map;

/** One answer: choice ids, the text of each blank, or the essay text. Same shape as Attempt in the frontend lib/grading.ts. */
public record AnswerContent(List<String> choices, Map<String, String> blanks, String essay) {
    public AnswerContent {
        if (choices == null) choices = List.of();
        if (blanks == null) blanks = Map.of();
        if (essay == null) essay = "";
    }
}
