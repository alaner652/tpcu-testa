package tw.testa.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Choice answers written in an exam paper, found by rules instead of the model. A small model fills in answers
 * even when the paper has none (and is often right, which makes it worse), so a choice answer counts only when
 * the paper holds it in one of the usual forms:
 * an answer table (a row of letters, as in national exam keys), a run of "1.C 2.ACE", "答：C", or "（C）1." before a question.
 * Lone matches such as "2B 鉛筆", "X5 年" or "A 國" are left out on purpose.
 */
final class AnswerKey {
    private static final String SEP = "\\s,，、;；|│";
    private static final Pattern PAIR = Pattern.compile(
            "(?<![\\d.A-Za-z])(\\d{1,3})\\s*[.．、:：]?\\s*([A-Z]{1,6})(?=$|[" + SEP + "])");
    private static final Pattern LEAD = Pattern.compile("^\\s*[(（]\\s*([A-Z]{1,6})\\s*[)）]\\s*\\d{1,3}\\s*[.．、](?!\\d)");
    private static final Pattern SAID = Pattern.compile("答案?\\s*[:：]\\s*[(（]?\\s*([A-Z]{1,6})\\s*[)）]?(?![A-Za-z])");
    private static final Pattern WORDS = Pattern.compile("正確答案|參考答案|標準答案|答案|解答|題號|答|第\\s*\\d+\\s*題");
    private static final Pattern TOKEN = Pattern.compile("[(（]?([A-Z]{1,6})[)）]?");
    private static final Pattern SPLIT = Pattern.compile("[" + SEP + "]+");

    private AnswerKey() {}

    /** The answers found, each with its letters sorted ("CA" becomes "AC"); empty when the paper has no answers. */
    static Set<String> of(String text) {
        List<String> found = new ArrayList<>();
        Map<Integer, String> single = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            String rest = WORDS.matcher(line).replaceAll(" ").strip();
            List<String> parts = Arrays.stream(SPLIT.split(rest)).filter(p -> !p.isEmpty()).toList();
            if (parts.size() >= 2 && parts.stream().allMatch(p -> TOKEN.matcher(p).matches())) {
                for (String p : parts) {
                    Matcher m = TOKEN.matcher(p);
                    m.matches();
                    found.add(m.group(1));
                }
                continue;
            }
            List<String[]> pairs = new ArrayList<>();
            Matcher pm = PAIR.matcher(rest);
            while (pm.find()) pairs.add(new String[] { pm.group(1), pm.group(2) });
            if (pairs.size() >= 2) {
                pairs.forEach(p -> found.add(p[1]));
                continue;
            }
            // One "7. C" per line counts only next to its neighbour ("8. D"), so a stray "30 A" does not
            if (pairs.size() == 1 && SPLIT.matcher(PAIR.matcher(rest).replaceAll("")).replaceAll("").isEmpty()) {
                single.put(Integer.parseInt(pairs.get(0)[0]), pairs.get(0)[1]);
                continue;
            }
            Matcher lead = LEAD.matcher(line);
            if (lead.find()) found.add(lead.group(1));
            Matcher said = SAID.matcher(line);
            while (said.find()) found.add(said.group(1));
        }
        single.forEach((n, a) -> {
            if (single.containsKey(n - 1) || single.containsKey(n + 1)) found.add(a);
        });
        Set<String> key = new HashSet<>();
        for (String a : found) key.add(sorted(a));
        return key;
    }

    static String sorted(String letters) {
        char[] c = letters.toCharArray();
        Arrays.sort(c);
        return new String(c);
    }
}
