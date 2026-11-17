package tw.testa.attempt;

import java.math.BigDecimal;
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
import tw.testa.config.BackupMaintenanceService;
import tw.testa.settings.SettingsService;
import tw.testa.exam.Candidate;
import tw.testa.exam.CandidateRepository;
import tw.testa.exam.Exam;
import tw.testa.exam.ExamSnapshot;
import tw.testa.exam.LoginCodes;
import tw.testa.grading.GradingDtos.CandidateResult;
import tw.testa.grading.GradingService;

/**
 * Candidate side: sign-in, start, save answers, submit, auto-grade.
 * Timing and grading happen only here. The frontend countdown is a hint; after deadline_at the server refuses saves.
 */
@Service
public class AttemptService {
    private static final Logger log = LoggerFactory.getLogger(AttemptService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ANON_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    /** Grace for network latency: the last save sent at the deadline is still accepted. */
    private static final Duration GRACE = Duration.ofSeconds(10);
    /** Maximum total extra minutes per candidate. */
    public static final int MAX_EXTRA_MINUTES = 120;
    private static final String BAD_LOGIN = "准考證號或登入碼不正確。請確認登入單上的資料，或洽監考人員。";

    private final CandidateRepository candidateRepo;
    private final AttemptRepository attemptRepo;
    private final AnswerRepository answerRepo;
    private final GradingService grading;
    private final AuditService audit;
    private final SettingsService settings;
    private final BackupMaintenanceService maintenance;
    private final TransactionTemplate tx;

    public AttemptService(CandidateRepository candidateRepo, AttemptRepository attemptRepo, AnswerRepository answerRepo,
            GradingService grading, AuditService audit, SettingsService settings, BackupMaintenanceService maintenance,
            TransactionTemplate tx) {
        this.settings = settings;
        this.maintenance = maintenance;
        this.grading = grading;
        this.candidateRepo = candidateRepo;
        this.attemptRepo = attemptRepo;
        this.answerRepo = answerRepo;
        this.audit = audit;
        this.tx = tx;
    }

    // ── Sign-in ──────────────────────────────────────────

    /**
     * Candidate number + login code. Only rostered candidates of a published exam get in.
     * A failed sign-in (ApiException) still commits, so failure counts, lockouts and the audit log persist;
     * the audit entry joins this transaction (recordAs), so a sign-in uses a single connection.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Candidate login(String rawTicket, String rawCode) {
        String ticket = rawTicket.trim().toUpperCase();
        String code = rawCode.replaceAll("\\s", "");
        Instant now = Instant.now();
        // The same candidate number can appear in several exams, each with its own login code
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
        Candidate match = found.stream().filter(c -> LoginCodes.matches(code, c.getLoginCodeHash()))
                // With several matches, prefer an exam that has not ended
                .min((a, b) -> Boolean.compare(a.getExam().getStatus() == Exam.Status.ENDED,
                        b.getExam().getStatus() == Exam.Status.ENDED))
                .orElse(null);
        if (match == null) {
            for (Candidate c : found) {
                c.setFailedLogins(c.getFailedLogins() + 1);
                if (c.getFailedLogins() >= settings.loginMaxFailures()) {
                    c.setLockedUntil(now.plus(Duration.ofMinutes(settings.loginLockMinutes())));
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

    // ── Answering ────────────────────────────────────────

    @Transactional
    public State state(Long candidateId) {
        Candidate c = candidate(candidateId);
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId).orElse(null);
        Instant now = Instant.now();
        if (a != null) closeIfOverdue(a, now);
        return toState(c, a, now);
    }

    /** Starts the attempt (or resumes it). The clock starts here. */
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

    /** The candidate left the exam screen (switched tab or window). Recorded, not penalised. */
    @Transactional
    public void left(Long candidateId) {
        Candidate c = candidate(candidateId);
        if (c.getStatus() == Candidate.Status.IN) c.left();
    }

    // ── Auto-submit ──────────────────────────────────────

    /** Submits and grades attempts that ran out of time. */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void sweep() {
        if (maintenance.active()) return;
        for (Long id : attemptRepo.findOverdueIds(Instant.now().minus(GRACE))) {
            try {
                tx.executeWithoutResult(s -> attemptRepo.findForUpdate(id).ifPresent(a -> {
                    if (!a.isSubmitted()) finalizeAttempt(a, a.getDeadlineAt(), true);
                }));
            } catch (RuntimeException e) {
                log.error("Auto-submit failed for attempt {}", id, e);
            }
        }
    }

    /** The officer ended the exam: submit every open attempt. Caller holds a transaction. */
    @Transactional
    public void closeAll(Long examId) {
        Instant now = Instant.now();
        for (Long id : attemptRepo.findOpenIdsByExam(examId)) {
            attemptRepo.findForUpdate(id).ifPresent(a -> {
                if (!a.isSubmitted()) finalizeAttempt(a, now.isAfter(a.getDeadlineAt()) ? a.getDeadlineAt() : now, true);
            });
        }
    }

    /** Each candidate's attempt in this exam (deadline and extra time for the proctoring page). */
    @Transactional(readOnly = true)
    public Map<Long, Attempt> byCandidate(Long examId) {
        Map<Long, Attempt> map = new HashMap<>();
        for (Attempt a : attemptRepo.findByExamId(examId)) map.put(a.getCandidate().getId(), a);
        return map;
    }

    /** Extra time from a proctor. The caller (ExamService) checks permissions and holds a transaction. */
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

    // ── Internal ─────────────────────────────────────────

    private Candidate candidate(Long id) {
        return candidateRepo.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "請重新登入。"));
    }

    /** The open attempt, locked; 409 if already submitted or overdue. */
    private Attempt openAttempt(Long candidateId, Instant now, Duration grace) {
        Attempt a = attemptRepo.findForUpdateByCandidateId(candidateId)
                .orElseThrow(() -> ApiException.conflict("not_started", "你還沒有開始作答。"));
        if (!a.isSubmitted() && a.getExam().getStatus() == Exam.Status.ENDED) finalizeAttempt(a, now, true);
        if (!a.isSubmitted() && now.isAfter(a.getDeadlineAt().plus(grace))) {
            // Do not submit here: throwing rolls the transaction back. state() or the scheduler will close it.
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

    /** Submits and auto-grades choice and fill-in questions. */
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
                new ExamInfo(e.getName(), e.getStartAt(), e.getDuration(), e.getMode(), e.phase(now), count, total, e.isCandidateResults()),
                a == null ? null : new AttemptInfo(a.getStartedAt(), a.getDeadlineAt(), a.getSubmittedAt(), remaining(a, now)),
                result(c));
    }

    /** After results are published, and if the exam allows it, candidates see their own scores (scores only). c always comes from the session. */
    private Result result(Candidate c) {
        CandidateResult r = grading.candidateResult(c.getExam(), c.getId());
        return r == null ? null
                : new Result(r.publishedAt(), r.fullMarks(), r.autoScore(), r.essayScore(), r.total());
    }

    /** Strips every answer before a question goes to a candidate: correct choices, blank answers, explanation, rubric. */
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
