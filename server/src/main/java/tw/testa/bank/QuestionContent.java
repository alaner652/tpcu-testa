package tw.testa.bank;

import java.util.List;

/**
 * 題目裡依題型而異的部分，整包存成 question.content（jsonb）。
 * 所有題型共用同一個結構，切換題型時不會遺失已輸入的內容（與前端一致）。
 * 注意：correct、blanks 的答案、explanation、rubric 都是「答案」，絕對不能送到考生端。
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
