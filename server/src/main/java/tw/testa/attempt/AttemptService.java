package tw.testa.attempt;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tw.testa.attempt.AttemptDtos.AttemptInfo;
import tw.testa.attempt.AttemptDtos.ExamInfo;
import tw.testa.attempt.AttemptDtos.Paper;
import tw.testa.attempt.AttemptDtos.PaperBlank;
import tw.testa.attempt.AttemptDtos.PaperChoice;
import tw.testa.attempt.AttemptDtos.PaperQuestion;
import tw.testa.attempt.AttemptDtos.Result;
import tw.testa.attempt.AttemptDtos.State;
import tw.testa.audit.AuditService;
import tw.testa.bank.BankDtos.QuestionDto;
import tw.testa.common.ApiException;
import tw.testa.config.TestaProperties;
import tw.testa.exam.Candidate;
import tw.testa.exam.CandidateRepository;
import tw.testa.exam.Exam;
import tw.testa.exam.ExamSnapshot;
import tw.testa.grading.GradingDtos.CandidateResult;
import tw.testa.grading.GradingService;

/**
 * 考生端：登入、開始作答、存答案、交卷、自動批改。
 * 原則：計時與批改只在這裡做。前端顯示的倒數只是參考，過了 deadline_at 伺服器就拒收。
 */
@Service
public class AttemptService {
    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ANON_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** 網路延遲的寬限：時間到的那一刻送出的最後一次存檔還是收 */
    private static final Duration GRACE = Duration.ofSeconds(10);
    /** 一位考生累計最多補時幾分鐘 */
    public static final int MAX_EXTRA_MINUTES = 120;
    private static final String BAD_LOGIN = "准考證號或登入碼不正確。請確認登入單上的資料，或洽監考人員。";

    private final CandidateRepository candidateRepo;
    private final AttemptRepository attemptRepo;
    private final AnswerRepository answerRepo;
    private final GradingService grading;
    private final AuditService audit;
    private final TestaProperties props;
    private final TransactionTemplate tx;

    public AttemptService(CandidateRepository candidateRepo, AttemptRepository attemptRepo, AnswerRepository answerRepo,
            GradingService grading, AuditService audit, TestaProperties props, TransactionTemplate tx) {
        this.grading = grading;
        this.candidateRepo = candidateRepo;
        this.attemptRepo = attemptRepo;
        this.answerRepo = answerRepo;
        this.audit = audit;
        this.props = props;
        this.tx = tx;
    }

    // ── 登入 ─────────────────────────────────────────────

    /**
     * 准考證號 + 登入碼。只有名單上、而且考試已發布的考生進得來。
     * 登入失敗（ApiException）交易照樣提交，所以失敗次數、鎖定、操作紀錄都會留下來；
     * 操作紀錄跟著這個交易寫（recordAs），整個登入只用一條資料庫連線。
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Candidate login(String rawTicket, String rawCode) {
        String ticket = rawTicket.trim().toUpperCase();
        String code = rawCode.replaceAll("\\s", "");
        Instant now = Instant.now();
        // 同一個准考證號可能出現在不同場考試，各有各的登入碼
        List<Candidate> found = candidateRepo.findByTicketWithExam(ticket).stream()
                .filter(c -> c.getExam().getStatus() != Exam.Status.DRAFT && !c.getExam().isArchived()).toList();
        if (found.isEmpty()) {
            audit.recordAs("CANDIDATE_LOGIN_FAILED", "ANONYMOUS", null, ticket, "unknown_ticket");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", BAD_LOGIN);
        }
        if (found.stream().anyMatch(c -> c.getLockedUntil() != null && c.getLockedUntil().isAfter(now))) {
            audit.recordAs("CANDIDATE_LOGIN_FAILED", "ANONYMOUS", null, ticket, "locked");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "locked", "登入碼輸錯太多次，已暫時鎖定。請洽監考人員重設登入碼。");
        }
        Candidate match = found.stream().filter(c -> sameCode(c.getLoginCode(), code))
                // 有多場時優先進還沒結束的那一場
                .min((a, b) -> Boolean.compare(a.getExam().getStatus() == Exam.Status.ENDED,
                        b.getExam().getStatus() == Exam.Status.ENDED))
                .orElse(null);
        if (match == null) {
            for (Candidate c : found) {
                c.setFailedLogins(c.getFailedLogins() + 1);
                if (c.getFailedLogins() >= props.login().maxFailures()) {
                    c.setLockedUntil(now.plus(Duration.ofMinutes(props.login().lockMinutes())));
                    c.setFailedLogins(0);
                }
            }
            audit.recordAs("CANDIDATE_LOGIN_FAILED", "ANONYMOUS", null, ticket, "bad_code");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", BAD_LOGIN);
        }
        match.setFailedLogins(0);
        match.setLockedUntil(null);
        boolean replaced = match.getSessionVersion() > 0 && match.getStatus() == Candidate.Status.IN;
        match.newSession();
        audit.recordAs("CANDIDATE_LOGIN", "CANDIDATE", match.getId(), ticket, replaced ? "replaced_session" : null);
        return match;
    }

    private static boolean sameCode(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    // ── 作答 ─────────────────────────────────────────────

    @Transactional
    public State state(Long candidateId) {
        Candidate c = candidate(candidateId);
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId).orElse(null);
        Instant now = Instant.now();
        if (a != null) closeIfOverdue(a, now);
        return toState(c, a, now);
    }

    /** 開始作答（或回到進行中的作答）。從這一刻起算時間。 */
    @Transactional
    public State start(Long candidateId) {
        Candidate c = candidate(candidateId);
        Exam e = c.getExam();
        Instant now = Instant.now();
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId).orElse(null);
        if (a == null) {
            String phase = e.phase(now);
            if (phase.equals("scheduled")) throw ApiException.conflict("not_started", "考試還沒開始，時間到了再進來。");
            if (!phase.equals("live")) throw ApiException.conflict("ended", "這場考試已經結束了。");
            a = attemptRepo.save(new Attempt(c, e, now, now.plus(Duration.ofMinutes(e.getDuration())), anonId()));
            c.setStatus(Candidate.Status.IN);
            audit.record("ATTEMPT_STARTED", "exam", e.getId(), e.getUnit().getId(), null, null);
        } else {
            closeIfOverdue(a, now);
        }
        return toState(c, a, now);
    }

    @Transactional
    public Paper paper(Long candidateId) {
        Attempt a = openAttempt(candidateId, Instant.now(), Duration.ZERO);
        Map<String, AnswerContent> answers = new HashMap<>();
        for (Answer x : answerRepo.findByAttemptId(a.getId())) answers.put(x.getQuestionId(), x.getContent());
        ExamSnapshot snap = a.getExam().getSnapshot();
        return new Paper(a.getExam().getName(), snap.questions().stream().map(AttemptService::strip).toList(), answers,
                a.getDeadlineAt(), remaining(a, Instant.now()));
    }

    @Transactional
    public long saveAnswer(Long candidateId, String questionId, AnswerContent content) {
        Instant now = Instant.now();
        Attempt a = openAttempt(candidateId, now, GRACE);
        QuestionDto q = a.getExam().getSnapshot().questions().stream().filter(x -> x.id().equals(questionId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("這場考試沒有這一題。"));
        validate(q, content);
        Answer answer = answerRepo.findByAttemptIdAndQuestionId(a.getId(), questionId).orElse(null);
        if (answer == null) answerRepo.save(new Answer(a, questionId, content));
        else answer.setContent(content);
        return remaining(a, now);
    }

    @Transactional
    public State submit(Long candidateId) {
        Candidate c = candidate(candidateId);
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.conflict("not_started", "你還沒有開始作答。"));
        Instant now = Instant.now();
        if (!a.isSubmitted()) finalizeAttempt(a, now.isAfter(a.getDeadlineAt()) ? a.getDeadlineAt() : now, false);
        return toState(c, a, now);
    }

    /** 作答中離開考試畫面（切換分頁、切到別的視窗）。只記錄，不處罰。 */
    @Transactional
    public void left(Long candidateId) {
        Candidate c = candidate(candidateId);
        if (c.getStatus() == Candidate.Status.IN) c.left();
    }

    // ── 系統收卷 ─────────────────────────────────────────

    /** 時間到還沒交卷的，由系統收卷並批改。 */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void sweep() {
        for (Long id : attemptRepo.findOverdueIds(Instant.now().minus(GRACE))) {
            try {
                tx.executeWithoutResult(s -> attemptRepo.findForUpdate(id).ifPresent(a -> {
                    if (!a.isSubmitted()) finalizeAttempt(a, a.getDeadlineAt(), true);
                }));
            } catch (RuntimeException e) {
                log.error("自動收卷失敗，attempt {}", id, e);
            }
        }
    }

    /** 承辦人結束考試：還在作答的全部收卷。呼叫端已在交易中。 */
    @Transactional
    public void closeAll(Long examId) {
        Instant now = Instant.now();
        for (Long id : attemptRepo.findOpenIdsByExam(examId)) {
            attemptRepo.findForUpdate(id).ifPresent(a -> {
                if (!a.isSubmitted()) finalizeAttempt(a, now.isAfter(a.getDeadlineAt()) ? a.getDeadlineAt() : now, true);
            });
        }
    }

    /** 這場考試每位考生的作答（給監考頁顯示截止時間與補時）。 */
    @Transactional(readOnly = true)
    public Map<Long, Attempt> byCandidate(Long examId) {
        Map<Long, Attempt> map = new HashMap<>();
        for (Attempt a : attemptRepo.findByExamId(examId)) map.put(a.getCandidate().getId(), a);
        return map;
    }

    /** 監考補時。權限由呼叫端（ExamService）檢查，呼叫端已在交易中。 */
    @Transactional
    public Attempt extend(Long candidateId, int minutes) {
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.conflict("not_started", "這位考生還沒有開始作答，不需要補時。"));
        closeIfOverdue(a, Instant.now());
        if (a.isSubmitted()) throw ApiException.conflict("done", "這位考生已經交卷了，不能補時。");
        if (a.getExtraMinutes() + minutes > MAX_EXTRA_MINUTES) {
            throw ApiException.conflict("too_much", "每位考生最多補時 %d 分鐘，這位已經補了 %d 分鐘。"
                    .formatted(MAX_EXTRA_MINUTES, a.getExtraMinutes()));
        }
        a.extend(minutes);
        return a;
    }

    // ── 內部 ─────────────────────────────────────────────

    private Candidate candidate(Long id) {
        return candidateRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "請重新登入。"));
    }

    /** 取得還能作答的那次作答（並鎖住）；已交卷或逾時就丟 409。 */
    private Attempt openAttempt(Long candidateId, Instant now, Duration grace) {
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.conflict("not_started", "你還沒有開始作答。"));
        if (!a.isSubmitted() && a.getExam().getStatus() == Exam.Status.ENDED) finalizeAttempt(a, now, true);
        if (!a.isSubmitted() && now.isAfter(a.getDeadlineAt().plus(grace))) {
            // 這裡不直接收卷：丟例外會讓交易回復。交給 state()／排程去收。
            throw ApiException.conflict("time_up", "作答時間已經到了，系統已自動交卷。");
        }
        if (a.isSubmitted()) throw ApiException.conflict("submitted", "你已經交卷了，不能再修改答案。");
        return a;
    }

    private void closeIfOverdue(Attempt a, Instant now) {
        if (a.isSubmitted()) return;
        if (now.isAfter(a.getDeadlineAt().plus(GRACE))) finalizeAttempt(a, a.getDeadlineAt(), true);
        else if (a.getExam().getStatus() == Exam.Status.ENDED) finalizeAttempt(a, now, true);
    }

    /** 交卷並自動批改選擇題與填空題。 */
    private void finalizeAttempt(Attempt a, Instant at, boolean auto) {
        Map<String, Answer> answers = new HashMap<>();
        for (Answer x : answerRepo.findByAttemptId(a.getId())) answers.put(x.getQuestionId(), x);
        BigDecimal total = BigDecimal.ZERO;
        for (QuestionDto q : a.getExam().getSnapshot().questions()) {
            Answer answer = answers.get(q.id());
            BigDecimal score = AutoGrader.grade(q, answer == null ? null : answer.getContent());
            if (answer != null) answer.setAutoScore(score);
            if (score != null) total = total.add(score);
        }
        a.setAutoScore(total);
        a.setSubmittedAt(at);
        a.setAutoSubmitted(auto);
        a.getCandidate().setStatus(Candidate.Status.DONE);
        audit.record("ATTEMPT_SUBMITTED", "candidate", a.getCandidate().getId(), a.getExam().getUnit().getId(), null,
                auto ? "auto" : null);
    }

    private static void validate(QuestionDto q, AnswerContent c) {
        Set<String> choiceIds = new HashSet<>();
        q.choices().forEach(x -> choiceIds.add(x.id()));
        Set<String> blankIds = new HashSet<>();
        q.blanks().forEach(x -> blankIds.add(x.id()));
        boolean ok = c.choices().size() <= choiceIds.size() && choiceIds.containsAll(c.choices())
                && new HashSet<>(c.choices()).size() == c.choices().size()
                && (!q.type().equals("single") || c.choices().size() <= 1)
                && blankIds.containsAll(c.blanks().keySet())
                && c.blanks().values().stream().allMatch(v -> v != null && v.length() <= 500)
                && c.essay().length() <= 50000;
        if (!ok) throw ApiException.badRequest("bad_answer", "作答內容的格式不正確。");
    }

    private static long remaining(Attempt a, Instant now) {
        if (a.isSubmitted()) return 0;
        return Math.max(0, Duration.between(now, a.getDeadlineAt()).getSeconds());
    }

    private State toState(Candidate c, Attempt a, Instant now) {
        Exam e = c.getExam();
        ExamSnapshot snap = e.getSnapshot();
        int count = snap == null ? 0 : snap.questions().size();
        BigDecimal total = snap == null ? BigDecimal.ZERO
                : snap.questions().stream().map(QuestionDto::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new State(c.getName(), c.getTicket(), c.getDept(), c.getSeat(),
                new ExamInfo(e.getName(), e.getStartAt(), e.getDuration(), e.getMode(), e.phase(now), count, total),
                a == null ? null : new AttemptInfo(a.getStartedAt(), a.getDeadlineAt(), a.getSubmittedAt(), remaining(a, now)),
                result(c));
    }

    /** 成績公布之後，考生看得到自己的分數（只有分數）。c 一律來自 Session 裡的考生身分。 */
    private Result result(Candidate c) {
        CandidateResult r = grading.candidateResult(c.getExam(), c.getId());
        return r == null ? null
                : new Result(r.publishedAt(), r.fullMarks(), r.autoScore(), r.essayScore(), r.total());
    }

    /** 把題目送到考生端之前去掉所有答案：正確選項、填空答案、解析、評分規準。 */
    private static PaperQuestion strip(QuestionDto q) {
        boolean choice = q.type().equals("single") || q.type().equals("multiple");
        return new PaperQuestion(q.id(), q.type(), q.stem(), q.points(),
                choice ? q.choices().stream().map(c -> new PaperChoice(c.id(), c.text())).toList() : List.of(),
                q.type().equals("blank") ? q.blanks().stream().map(b -> new PaperBlank(b.id())).toList() : List.of(),
                q.type().equals("essay") ? q.wordLimit() : null);
    }

    private static String anonId() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(ANON_CHARS.charAt(RANDOM.nextInt(ANON_CHARS.length())));
        return sb.toString();
    }
}
