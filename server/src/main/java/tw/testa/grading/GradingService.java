package tw.testa.grading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.bank.BankDtos.QuestionDto;
import tw.testa.common.ApiException;
import tw.testa.config.TestaProperties;
import tw.testa.exam.Exam;
import tw.testa.exam.ExamRepository;
import tw.testa.grading.GradingDtos.CandidateResult;
import tw.testa.grading.GradingDtos.GraderInfo;
import tw.testa.grading.GradingDtos.GradingExam;
import tw.testa.grading.GradingDtos.Item;
import tw.testa.grading.GradingDtos.ItemSummary;
import tw.testa.grading.GradingDtos.ResultRow;
import tw.testa.grading.GradingDtos.Results;
import tw.testa.unit.Role;

/**
 * 申論題評分與成績。
 * - 承辦人為每場考試指派 1 到多位評分者（必須是該單位的「評分」角色）。
 * - 評分者只看到匿名編號與作答，看不到考生身分，也看不到其他人的分數；每個人看到的順序不同。
 * - 一個答案被所有指派的評分者送出後即「鎖定」，得分 = 平均（四捨五入到小數一位）。
 * - 考生總分 = 自動批改得分 + 各申論題平均。空白的申論答案直接 0 分，不需要評。
 * - 分數一律即時由 grading 紀錄算出，不另外存，避免兩邊對不上。
 */
@Service
public class GradingService {
    private final JdbcTemplate jdbc;
    private final ExamRepository examRepo;
    private final AccessGuard access;
    private final AuditService audit;
    private final TestaProperties props;

    public GradingService(JdbcTemplate jdbc, ExamRepository examRepo, AccessGuard access, AuditService audit,
            TestaProperties props) {
        this.jdbc = jdbc;
        this.examRepo = examRepo;
        this.access = access;
        this.audit = audit;
        this.props = props;
    }

    /** 一個需要人工評分的申論答案。 */
    private record Essay(long attemptId, String anonId, String questionId, String text) {}

    private record Given(long attemptId, String questionId, long staffId, BigDecimal score, String comment) {}

    // ── 評分者 ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GradingExam> myExams() {
        Long me = access.current().id();
        List<GradingExam> out = new ArrayList<>();
        for (Long examId : jdbc.queryForList("select exam_id from exam_grader where staff_id = ? order by exam_id desc", Long.class, me)) {
            Exam e = examRepo.findById(examId).orElse(null);
            if (e == null || e.getSnapshot() == null || !access.hasUnitRole(e.getUnit().getId(), Role.GRADER)) continue;
            List<Essay> essays = essays(e);
            Set<String> mine = new HashSet<>();
            for (Given g : given(examId)) if (g.staffId() == me) mine.add(g.attemptId() + "/" + g.questionId());
            int done = (int) essays.stream().filter(x -> mine.contains(x.attemptId() + "/" + x.questionId())).count();
            out.add(new GradingExam(examId, e.getName(), essays.size(), done));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Item> items(Long examId) {
        Exam e = gradableExam(examId);
        Long me = access.current().id();
        Map<String, Given> mine = new HashMap<>();
        for (Given g : given(examId)) if (g.staffId() == me) mine.put(g.attemptId() + "/" + g.questionId(), g);
        Map<String, QuestionDto> questions = essayQuestions(e);
        List<String> order = e.getSnapshot().questions().stream().map(QuestionDto::id).toList();
        List<Essay> essays = new ArrayList<>(essays(e));
        // 每位評分者看到的順序不同（同一個人每次看到的順序固定）
        essays.sort(Comparator.comparingInt((Essay x) -> Objects.hash(me, x.anonId(), x.questionId())));
        return essays.stream().map(x -> {
            QuestionDto q = questions.get(x.questionId());
            Given g = mine.get(x.attemptId() + "/" + x.questionId());
            return new Item(x.anonId(), x.questionId(), order.indexOf(x.questionId()) + 1, q.stem(), q.rubric(), q.points(),
                    x.text(), g == null ? null : g.score(), g == null ? null : g.comment());
        }).toList();
    }

    /** 送出一個答案的分數。送出後自己不能再改。 */
    @Transactional
    public void score(Long examId, String anonId, String questionId, BigDecimal score, String comment) {
        Exam e = gradableExam(examId);
        Essay essay = essays(e).stream().filter(x -> x.anonId().equals(anonId) && x.questionId().equals(questionId))
                .findFirst().orElseThrow(() -> ApiException.notFound("找不到這份答卷。"));
        QuestionDto q = essayQuestions(e).get(questionId);
        if (score.compareTo(q.points()) > 0) {
            throw ApiException.badRequest("over_points", "給分不能超過滿分 " + q.points().stripTrailingZeros().toPlainString() + " 分。");
        }
        if (score.multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest("bad_step", "分數請以 0.5 分為單位。");
        }
        int inserted = jdbc.update("""
                insert into grading (attempt_id, question_id, staff_id, score, comment) values (?, ?, ?, ?, ?)
                on conflict (attempt_id, question_id, staff_id) do nothing""", essay.attemptId(), questionId,
                access.current().id(), score, comment == null ? "" : comment.trim());
        if (inserted == 0) {
            throw ApiException.conflict("already_graded", "你已經送出這份答卷的分數了。需要修改請洽承辦人重開評分。");
        }
    }

    // ── 承辦人 ───────────────────────────────────────────

    /** 這個單位所有有「評分」角色的人，以及誰被指派、評了多少。看不到分數。 */
    @Transactional(readOnly = true)
    public List<GraderInfo> graders(Long examId) {
        Exam e = ownedExam(examId);
        Set<Long> assigned = new HashSet<>(assignedGraders(examId));
        int total = e.getSnapshot() == null ? 0 : essays(e).size();
        Map<Long, Integer> done = new HashMap<>();
        for (Given g : given(examId)) done.merge(g.staffId(), 1, Integer::sum);
        return jdbc.query("""
                select s.id, s.name, s.email from staff s
                where s.active and (exists (select 1 from unit_role r where r.staff_id = s.id and r.unit_id = ? and r.role = 'GRADER')
                       or exists (select 1 from exam_grader g where g.staff_id = s.id and g.exam_id = ?))
                order by s.name, s.id""",
                (rs, i) -> new GraderInfo(rs.getLong(1), rs.getString(2), rs.getString(3), assigned.contains(rs.getLong(1)),
                        done.getOrDefault(rs.getLong(1), 0), total),
                e.getUnit().getId(), examId);
    }

    @Transactional
    public List<GraderInfo> setGraders(Long examId, List<Long> staffIds) {
        Exam e = ownedExam(examId);
        if (e.getResultsPublishedAt() != null) throw ApiException.conflict("published", "成績已經公布，不能再更改評分者。");
        Set<Long> wanted = new HashSet<>(staffIds);
        Set<Long> current = new HashSet<>(assignedGraders(examId));
        Set<Long> graded = new HashSet<>();
        for (Given g : given(examId)) graded.add(g.staffId());
        for (Long id : current) {
            if (wanted.contains(id)) continue;
            // 已經送出分數的評分者不能直接移除，否則分數會無聲無息消失
            if (graded.contains(id)) {
                throw ApiException.conflict("has_gradings", "這位評分者已經送出分數，不能移除。");
            }
            jdbc.update("delete from exam_grader where exam_id = ? and staff_id = ?", examId, id);
            audit.record("GRADER_UNASSIGNED", "staff", id, e.getUnit().getId(), null, e.getName());
        }
        for (Long id : wanted) {
            if (current.contains(id)) continue;
            Integer ok = jdbc.queryForObject("""
                    select count(*) from unit_role r join staff s on s.id = r.staff_id
                    where r.staff_id = ? and r.unit_id = ? and r.role = 'GRADER' and s.active""", Integer.class, id,
                    e.getUnit().getId());
            if (ok == null || ok == 0) {
                throw ApiException.badRequest("not_grader", "只能指派這個單位裡有「評分」角色的教職員。");
            }
            jdbc.update("insert into exam_grader (exam_id, staff_id) values (?, ?)", examId, id);
            audit.record("GRADER_ASSIGNED", "staff", id, e.getUnit().getId(), null, e.getName());
        }
        return graders(examId);
    }

    @Transactional(readOnly = true)
    public Results results(Long examId) {
        Exam e = ownedExam(examId);
        return compute(e);
    }

    /** 重開一個答案的評分：刪掉所有人對它的分數，請評分者重評。必須填理由。 */
    @Transactional
    public void reopen(Long examId, String anonId, String questionId, String reason) {
        Exam e = ownedExam(examId);
        if (e.getResultsPublishedAt() != null) throw ApiException.conflict("published", "成績已經公布，不能重開評分。");
        Long attemptId = jdbc.query("select id from attempt where exam_id = ? and anon_id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, examId, anonId);
        if (attemptId == null) throw ApiException.notFound("找不到這份答卷。");
        List<String> old = jdbc.queryForList(
                "select score::text from grading where attempt_id = ? and question_id = ? order by staff_id", String.class,
                attemptId, questionId);
        if (old.isEmpty()) throw ApiException.conflict("not_graded", "這份答卷還沒有人評分。");
        jdbc.update("delete from grading where attempt_id = ? and question_id = ?", attemptId, questionId);
        audit.record("GRADING_REOPENED", "attempt", anonId, e.getUnit().getId(), reason.trim(),
                questionId + " 原分數 " + String.join("、", old));
    }

    @Transactional
    public Results publish(Long examId) {
        Exam e = ownedExam(examId);
        if (e.getResultsPublishedAt() != null) throw ApiException.conflict("published", "成績已經公布過了。");
        if (e.getStatus() != Exam.Status.ENDED) throw ApiException.conflict("not_ended", "考試結束後才能公布成績。");
        Results r = compute(e);
        if (!r.complete()) throw ApiException.conflict("incomplete", "還有申論答案沒有評完，全部評分完成後才能公布成績。");
        e.setResultsPublishedAt(Instant.now());
        audit.record("RESULTS_PUBLISHED", "exam", examId, e.getUnit().getId(), null, e.getName());
        return compute(e);
    }

    private static final String[] EXPORT_HEADERS = {"座位", "准考證號", "姓名", "報考科系", "狀態", "自動批改", "申論", "總分"};

    private static String statusLabel(ResultRow row) {
        return switch (row.status()) {
            case "DONE" -> row.pending() > 0 ? "評分中" : "已完成";
            case "IN" -> "作答中";
            default -> "缺考";
        };
    }

    /** 成績表（CSV，UTF-8 含 BOM，Excel 可以直接開）。 */
    @Transactional
    public String exportCsv(Long examId) {
        Exam e = ownedExam(examId);
        Results r = compute(e);
        StringBuilder sb = new StringBuilder("\uFEFF" + String.join(",", EXPORT_HEADERS) + "\r\n");
        for (ResultRow row : r.rows()) {
            sb.append(row.seat()).append(',').append(csv(row.ticket())).append(',').append(csv(row.name())).append(',')
                    .append(csv(row.dept())).append(',').append(statusLabel(row)).append(',').append(num(row.autoScore())).append(',')
                    .append(num(row.essayScore())).append(',').append(num(row.total())).append("\r\n");
        }
        audit.record("RESULTS_EXPORTED", "exam", examId, e.getUnit().getId(), null, e.getName());
        return sb.toString();
    }

    /** 成績表（Excel .xlsx）。欄位與 CSV 相同；分數是數字格式，文字欄一律存成文字（不會被當成公式）。 */
    @Transactional
    public byte[] exportXlsx(Long examId) {
        Exam e = ownedExam(examId);
        Results r = compute(e);
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.createSheet("成績");
            var bold = wb.createFont();
            bold.setBold(true);
            var headStyle = wb.createCellStyle();
            headStyle.setFont(bold);
            var head = sheet.createRow(0);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                var c = head.createCell(i);
                c.setCellValue(EXPORT_HEADERS[i]);
                c.setCellStyle(headStyle);
            }
            int n = 1;
            for (ResultRow row : r.rows()) {
                var x = sheet.createRow(n++);
                x.createCell(0).setCellValue(row.seat());
                x.createCell(1).setCellValue(row.ticket());
                x.createCell(2).setCellValue(row.name());
                x.createCell(3).setCellValue(row.dept() == null ? "" : row.dept());
                x.createCell(4).setCellValue(statusLabel(row));
                BigDecimal[] scores = {row.autoScore(), row.essayScore(), row.total()};
                for (int i = 0; i < scores.length; i++) {
                    if (scores[i] != null) x.createCell(5 + i).setCellValue(scores[i].doubleValue());
                }
            }
            sheet.createFreezePane(0, 1);
            int[] widths = {6, 16, 14, 22, 10, 10, 10, 10};
            for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
            wb.write(out);
            audit.record("RESULTS_EXPORTED", "exam", examId, e.getUnit().getId(), null, e.getName());
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }

    // ── 考生 ─────────────────────────────────────────────

    /**
     * 考生查自己的成績：成績公布之後才有，公布前一律 null。
     * 這裡不檢查教職員權限，呼叫端（AttemptService）必須已經確認 candidateId 就是登入的考生本人。
     */
    @Transactional(readOnly = true)
    public CandidateResult candidateResult(Exam e, Long candidateId) {
        if (e.getResultsPublishedAt() == null) return null;
        String ticket = jdbc.query("select ticket from candidate where id = ? and exam_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, candidateId, e.getId());
        if (ticket == null) return null;
        Results r = compute(e);
        // 准考證號在同一場考試裡不會重複
        ResultRow row = r.rows().stream().filter(x -> x.ticket().equals(ticket)).findFirst().orElse(null);
        if (row == null) return null;
        return new CandidateResult(r.publishedAt(), r.fullMarks(), row.autoScore(), row.essayScore(), row.total());
    }

    // ── 內部 ─────────────────────────────────────────────

    private Results compute(Exam e) {
        Long examId = e.getId();
        List<Long> graders = assignedGraders(examId);
        List<Essay> essays = e.getSnapshot() == null ? List.of() : essays(e);
        Map<String, QuestionDto> questions = e.getSnapshot() == null ? Map.of() : essayQuestions(e);
        List<String> order = e.getSnapshot() == null ? List.of()
                : e.getSnapshot().questions().stream().map(QuestionDto::id).toList();
        Map<String, List<BigDecimal>> scores = new HashMap<>();
        for (Given g : given(examId)) {
            if (graders.contains(g.staffId())) {
                scores.computeIfAbsent(g.attemptId() + "/" + g.questionId(), k -> new ArrayList<>()).add(g.score());
            }
        }
        List<ItemSummary> items = new ArrayList<>();
        Map<Long, BigDecimal> essayScore = new HashMap<>();
        Map<Long, Integer> pending = new HashMap<>();
        int locked = 0;
        for (Essay x : essays) {
            QuestionDto q = questions.get(x.questionId());
            List<BigDecimal> s = scores.getOrDefault(x.attemptId() + "/" + x.questionId(), List.of());
            boolean isLocked = !graders.isEmpty() && s.size() >= graders.size();
            BigDecimal avg = null;
            boolean flagged = false;
            if (isLocked) {
                locked++;
                avg = s.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(s.size()), 1, RoundingMode.HALF_UP);
                essayScore.merge(x.attemptId(), avg, BigDecimal::add);
                BigDecimal spread = s.stream().max(Comparator.naturalOrder()).get().subtract(s.stream().min(Comparator.naturalOrder()).get());
                flagged = s.size() > 1 && spread.doubleValue() > q.points().doubleValue() * props.grading().flagRatio();
            } else {
                pending.merge(x.attemptId(), 1, Integer::sum);
            }
            items.add(new ItemSummary(x.anonId(), x.questionId(), order.indexOf(x.questionId()) + 1, q.points(), s.size(),
                    graders.size(), isLocked, isLocked ? s.stream().sorted().toList() : List.of(), avg, flagged));
        }
        // 需要注意的排前面
        items.sort(Comparator.comparing((ItemSummary i) -> !i.flagged()).thenComparing(ItemSummary::locked)
                .thenComparing(ItemSummary::anonId).thenComparingInt(ItemSummary::number));

        List<ResultRow> rows = jdbc.query("""
                select c.ticket, c.name, c.dept, c.seat, c.status, a.id, a.auto_score
                from candidate c left join attempt a on a.candidate_id = c.id
                where c.exam_id = ? order by c.seat, c.id""", (rs, i) -> {
            Long attemptId = (Long) rs.getObject(6);
            String status = rs.getString(5);
            BigDecimal auto = rs.getBigDecimal(7);
            int p = attemptId == null ? 0 : pending.getOrDefault(attemptId, 0);
            BigDecimal essay = attemptId == null ? null : essayScore.getOrDefault(attemptId, BigDecimal.ZERO);
            boolean done = status.equals("DONE") && auto != null;
            return new ResultRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), status, done ? auto : null,
                    done ? essay : null, done && p == 0 ? auto.add(essay) : null, done ? p : 0);
        }, examId);
        boolean anyoneWriting = rows.stream().anyMatch(r -> r.status().equals("IN"));
        BigDecimal full = e.getSnapshot() == null ? BigDecimal.ZERO
                : e.getSnapshot().questions().stream().map(QuestionDto::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Results(full, graders.size(), essays.size(), locked, !anyoneWriting && locked == essays.size(),
                e.getResultsPublishedAt(), rows, items);
    }

    /** 已交卷、而且真的有寫東西的申論答案。 */
    private List<Essay> essays(Exam e) {
        Set<String> ids = essayQuestions(e).keySet();
        if (ids.isEmpty()) return List.of();
        return jdbc.query("""
                select a.id, a.anon_id, an.question_id, an.content->>'essay'
                from attempt a join answer an on an.attempt_id = a.id
                where a.exam_id = ? and a.submitted_at is not null
                  and length(btrim(coalesce(an.content->>'essay', ''))) > 0
                order by a.id, an.question_id""",
                (rs, i) -> new Essay(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), e.getId())
                .stream().filter(x -> ids.contains(x.questionId())).toList();
    }

    private List<Given> given(Long examId) {
        return jdbc.query("""
                select g.attempt_id, g.question_id, g.staff_id, g.score, g.comment
                from grading g join attempt a on a.id = g.attempt_id where a.exam_id = ?""",
                (rs, i) -> new Given(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getBigDecimal(4), rs.getString(5)), examId);
    }

    private List<Long> assignedGraders(Long examId) {
        return jdbc.queryForList("select staff_id from exam_grader where exam_id = ?", Long.class, examId);
    }

    private static Map<String, QuestionDto> essayQuestions(Exam e) {
        Map<String, QuestionDto> map = new LinkedHashMap<>();
        for (QuestionDto q : e.getSnapshot().questions()) if (q.type().equals("essay")) map.put(q.id(), q);
        return map;
    }

    /** 我被指派評分、而且仍有評分角色的考試；其他一律「找不到」。 */
    private Exam gradableExam(Long examId) {
        Exam e = examRepo.findById(examId).orElseThrow(() -> ApiException.notFound("找不到這場考試。"));
        boolean mine = assignedGraders(examId).contains(access.current().id())
                && access.hasUnitRole(e.getUnit().getId(), Role.GRADER) && e.getSnapshot() != null;
        if (!mine) throw ApiException.notFound("找不到這場考試。");
        if (e.getResultsPublishedAt() != null) throw ApiException.conflict("published", "成績已經公布，評分已關閉。");
        return e;
    }

    private Exam ownedExam(Long examId) {
        Exam e = examRepo.findById(examId).orElseThrow(() -> ApiException.notFound("找不到這場考試。"));
        if (!access.hasUnitRole(e.getUnit().getId(), Role.OWNER)) throw ApiException.notFound("找不到這場考試。");
        return e;
    }

    private static String num(BigDecimal n) {
        return n == null ? "" : n.stripTrailingZeros().toPlainString();
    }

    private static String csv(String s) {
        String v = s == null ? "" : s;
        // 避免 Excel 把開頭是 = + - @ 的內容當成公式執行
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return v.matches(".*[\",\\r\\n].*") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
