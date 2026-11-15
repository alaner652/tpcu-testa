package tw.testa.ai;

import java.util.List;

/** AI question import payloads. A draft matches the frontend's createQuestion() input; ids are assigned there. */
public final class AiDtos {
    private AiDtos() {}

    public record Status(boolean questionImport) {}

    /**
     * correct: indexes into choices. blanks: accepted answers per blank.
     * note: what the author should check, set by code when the model's output looks wrong.
     */
    public record Draft(String type, String stem, double points, List<String> choices, List<Integer> correct,
            List<List<String>> blanks, Integer wordLimit, String note) {}

    /** state: running, done or failed. */
    public record Job(String id, String state, List<Draft> questions, String error) {}
}
