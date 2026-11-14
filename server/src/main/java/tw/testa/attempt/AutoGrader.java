package tw.testa.attempt;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;

import tw.testa.bank.BankDtos.BlankDto;
import tw.testa.bank.BankDtos.QuestionDto;

/**
 * Auto-grading, same rules as the frontend lib/grading.ts:
 * choice must match exactly; fill-in scores by the share of correct blanks; essays are not auto-graded (null, awaiting graders).
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
