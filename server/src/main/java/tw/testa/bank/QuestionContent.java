package tw.testa.bank;

import java.util.List;

/**
 * The type-specific part of a question, stored as question.content (jsonb).
 * All types share one structure, so switching type keeps what was entered (as in the frontend).
 * correct, blank answers, explanation and rubric are all answers and must never reach a candidate.
 */
public record QuestionContent(
        String explanation,
        List<Choice> choices,
        List<String> correct,
        List<Blank> blanks,
        boolean caseSensitive,
        String rubric,
        Integer wordLimit,
        boolean aiGrading,
        boolean review) {

    public record Choice(String id, String text) {}

    public record Blank(String id, List<String> answers) {}
}
