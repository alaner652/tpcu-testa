package tw.testa.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tw.testa.ai.AiDtos.Draft;

/**
 * Turns the model's answer into drafts. The model is not trusted: code checks every field,
 * drops what cannot be right and leaves a note for the author instead of guessing.
 */
final class QuestionDrafts {
    private static final Set<String> TYPES = Set.of("single", "multiple", "blank", "essay");
    private static final Pattern CHOICE_LABEL = Pattern.compile("^\\s*[\\(（]?[A-Za-z][\\)）\\.．、:：]\\s*");
    private static final Pattern BLANK_MARK = Pattern.compile("[_＿]{2,}");
    private static final int MAX_QUESTIONS = 200;

    private QuestionDrafts() {}

    static List<Draft> from(JsonNode out, String source) {
        String plainSource = source.replaceAll("\\s+", "");
        List<Draft> drafts = new ArrayList<>();
        for (JsonNode q : out.path("questions")) {
            if (drafts.size() >= MAX_QUESTIONS) break;
            String type = q.path("type").asString("");
            String stem = q.path("stem").asString("").strip();
            if (!TYPES.contains(type) || stem.isEmpty()) continue;
            if (stem.length() > 20000) stem = stem.substring(0, 20000);
            List<String> notes = new ArrayList<>();

            double points = q.path("points").asDouble(0);
            if (!(points > 0 && points <= 100)) {
                points = type.equals("essay") ? 10 : 5;
                notes.add("看不出配分");
            }
            points = Math.round(points * 2) / 2.0;

            List<String> choices = new ArrayList<>();
            List<Integer> correct = new ArrayList<>();
            List<List<String>> blanks = new ArrayList<>();
            Integer wordLimit = null;

            if (type.equals("single") || type.equals("multiple")) {
                for (JsonNode c : q.path("choices")) {
                    String text = CHOICE_LABEL.matcher(c.asString("")).replaceFirst("").strip();
                    if (!text.isEmpty() && choices.size() < 26) choices.add(text.length() > 5000 ? text.substring(0, 5000) : text);
                }
                boolean unknown = false;
                for (JsonNode c : q.path("correct")) {
                    for (char ch : c.asString("").toUpperCase().toCharArray()) {
                        if (ch < 'A' || ch > 'Z') continue;
                        int index = ch - 'A';
                        if (index >= choices.size()) unknown = true;
                        else if (!correct.contains(index)) correct.add(index);
                    }
                }
                if (choices.size() < 2) notes.add("選項不足");
                if (unknown) {
                    correct.clear();
                    notes.add("答案對不上選項");
                } else if (correct.isEmpty()) {
                    notes.add("找不到答案");
                } else if (type.equals("single") && correct.size() > 1) {
                    correct.clear();
                    notes.add("單選題卻有多個答案");
                }
            } else if (type.equals("blank")) {
                for (JsonNode a : q.path("blankAnswers")) {
                    String answer = a.asString("").strip();
                    if (!answer.isEmpty() && blanks.size() < 50) blanks.add(List.of(answer.length() > 500 ? answer.substring(0, 500) : answer));
                }
                long marks = BLANK_MARK.matcher(stem).results().count();
                if (blanks.isEmpty()) {
                    blanks.add(List.of());
                    notes.add("找不到答案");
                } else if (marks > 0 && marks != blanks.size()) {
                    notes.add("空格數和答案數不一致");
                }
            } else {
                int limit = q.path("wordLimit").asInt(0);
                if (limit > 0 && limit <= 100000) wordLimit = limit;
            }

            // The opening of the stem must come from the paper; otherwise the model made it up or rewrote it
            String plainStem = stem.replaceAll("\\s+", "");
            String head = plainStem.substring(0, Math.min(8, plainStem.length()));
            if (!plainSource.contains(head)) notes.add("題目文字和原檔不同");

            drafts.add(new Draft(type, stem, points, choices, correct, blanks, wordLimit,
                    notes.isEmpty() ? null : String.join("、", notes) + "，請核對"));
        }
        return drafts;
    }
}
