package tw.testa.attempt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;

import tw.testa.bank.BankDtos.BlankDto;
import tw.testa.bank.BankDtos.QuestionDto;

/**
 * 自動批改，規則與前端 lib/grading.ts 相同：
 * 選擇題要完全相符；填空題依答對的空格數平均給分；申論題不自動批改（回傳 null，等人工評分）。
 */
final class AutoGrader {
    private AutoGrader() {}

    static BigDecimal grade(QuestionDto q, AnswerContent a) {
        if (q.type().equals("essay")) return null;
        if (a == null) return BigDecimal.ZERO;
        if (q.type().equals("single") || q.type().equals("multiple")) {
            boolean right = !a.choices().isEmpty() && new HashSet<>(a.choices()).equals(new HashSet<>(q.correct()));
            return right ? q.points() : BigDecimal.ZERO;
        }
        if (q.blanks().isEmpty()) return BigDecimal.ZERO;
        int hits = 0;
        for (BlankDto b : q.blanks()) {
            String given = normalize(a.blanks().getOrDefault(b.id(), ""), q.caseSensitive());
            if (!given.isEmpty() && b.answers().stream().anyMatch(x -> normalize(x, q.caseSensitive()).equals(given))) {
                hits++;
            }
        }
        return q.points().multiply(BigDecimal.valueOf(hits)).divide(BigDecimal.valueOf(q.blanks().size()), 2,
                RoundingMode.HALF_UP);
    }

    private static String normalize(String text, boolean caseSensitive) {
        String t = text.strip().replaceAll("\\s+", "");
        return caseSensitive ? t : t.toLowerCase();
    }
}
