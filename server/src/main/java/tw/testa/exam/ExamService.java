package tw.testa.exam;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.bank.Bank;
import tw.testa.bank.BankDtos.QuestionDto;
import tw.testa.bank.BankRepository;
import tw.testa.bank.BankService;
import tw.testa.common.ApiException;
import tw.testa.exam.ExamDtos.CandidateDto;
import tw.testa.exam.ExamDtos.CreateExamRequest;
import tw.testa.exam.ExamDtos.ExamDto;
import tw.testa.exam.ExamDtos.ImportResult;
import tw.testa.exam.ExamDtos.RosterPreview;
import tw.testa.exam.ExamDtos.RosterProblem;
import tw.testa.exam.ExamDtos.RosterRow;
import tw.testa.exam.ExamDtos.UpdateExamRequest;
import tw.testa.staff.StaffRepository;
import tw.testa.unit.Role;
import tw.testa.unit.UnitRole;
import tw.testa.unit.UnitRoleRepository;

/**
 * 考試與考生名單。權限：
 * - 單位負責人（OWNER）：建立、修改、發布、結束考試；匯入與移除考生。
 * - 監考（PROCTOR）：看考試與名單（含登入碼）、重設登入碼。負責人也可以。
 * - 出題者、評分者、系統管理員看不到名單。
 */
@Service
public class ExamService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExamRepository examRepo;
    private final CandidateRepository candidateRepo;
    private final BankRepository bankRepo;
    private final BankService bankService;
    private final UnitRoleRepository roleRepo;
    private final StaffRepository staffRepo;
    private final AccessGuard access;
    private final AuditService audit;
    private final tw.testa.attempt.AttemptService attempts;
    private final tw.testa.config.TestaProperties props;
    private static final java.time.ZoneId TAIPEI = java.time.ZoneId.of("Asia/Taipei");

    public ExamService(ExamRepository examRepo, CandidateRepository candidateRepo, BankRepository bankRepo,
            BankService bankService, UnitRoleRepository roleRepo, StaffRepository staffRepo, AccessGuard access,
            AuditService audit, @org.springframework.context.annotation.Lazy tw.testa.attempt.AttemptService attempts,
            tw.testa.config.TestaProperties props) {
        this.attempts = attempts;
        this.props = props;
        this.examRepo = examRepo;
        this.candidateRepo = candidateRepo;
        this.bankRepo = bankRepo;
        this.bankService = bankService;
        this.roleRepo = roleRepo;
        this.staffRepo = staffRepo;
        this.access = access;
        this.audit = audit;
    }

    // ── 考試 ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamDto> list() {
        // 單位 → 我是不是負責人
        Map<Long, Boolean> units = new HashMap<>();
        for (UnitRole r : roleRepo.findByStaffIdWithUnit(access.current().id())) {
            if (!r.getUnit().isActive()) continue;
            if (r.getRole() == Role.OWNER) units.put(r.getUnit().getId(), true);
            else if (r.getRole() == Role.PROCTOR) units.putIfAbsent(r.getUnit().getId(), false);
        }
        if (units.isEmpty()) return List.of();
        List<Exam> exams = examRepo.findByUnitIds(units.keySet());
        Map<Long, long[]> counts = counts(exams.stream().map(Exam::getId).toList());
        return exams.stream().map(e -> toDto(e, counts.get(e.getId()), units.get(e.getUnit().getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ExamDto get(Long id) {
        Exam e = findVisible(id);
        return toDto(e, counts(List.of(id)).get(id), access.hasUnitRole(e.getUnit().getId(), Role.OWNER));
    }

    @Transactional
    public ExamDto create(CreateExamRequest req) {
        Bank bank = bankRepo.findById(req.bankId()).orElseThrow(() -> ApiException.badRequest("unknown_bank", "找不到這份題庫。"));
        Long unitId = bank.getUnit().getId();
        // 考試建在題庫所屬的單位，必須是那個單位的負責人
        if (!access.hasUnitRole(unitId, Role.OWNER)) {
            if (access.hasUnitRole(unitId, Role.AUTHOR)) throw ApiException.forbidden();
            throw ApiException.badRequest("unknown_bank", "找不到這份題庫。");
        }
        Exam e = new Exam(bank.getUnit(), bank, staffRepo.getReferenceById(access.current().id()), req.name().trim(),
                req.duration(), req.mode() == null ? Exam.Mode.CLASSROOM : req.mode());
        e.setStartAt(req.startAt());
        examRepo.save(e);
        audit.record("EXAM_CREATED", "exam", e.getId(), unitId, null, e.getName());
        return toDto(e, null, true);
    }

    @Transactional
    public ExamDto update(Long id, UpdateExamRequest req) {
        Exam e = findManaged(id);
        if (e.getStatus() != Exam.Status.DRAFT) {
            throw ApiException.conflict("not_draft", "考試發布後不能修改。要修改請先取消發布。");
        }
        if (req.name() != null) e.setName(req.name().trim());
        if (req.duration() != null) e.setDuration(req.duration());
        if (req.mode() != null) e.setMode(req.mode());
        if (Boolean.TRUE.equals(req.clearStartAt())) e.setStartAt(null);
        else if (req.startAt() != null) e.setStartAt(req.startAt());
        if (req.bankId() != null && (e.getBank() == null || !req.bankId().equals(e.getBank().getId()))) {
            Bank bank = bankRepo.findById(req.bankId()).filter(b -> b.getUnit().getId().equals(e.getUnit().getId()))
                    .orElseThrow(() -> ApiException.badRequest("unknown_bank", "找不到這份題庫，或它不屬於這個單位。"));
            e.setBank(bank);
        }
        return toDto(e, counts(List.of(id)).get(id), true);
    }

    /** 發布：檢查能不能考，並把題目凍結成快照。 */
    @Transactional
    public ExamDto publish(Long id) {
        Exam e = findManaged(id);
        if (e.getStatus() != Exam.Status.DRAFT) throw ApiException.conflict("not_draft", "這場考試已經發布過了。");
        if (e.getStartAt() == null) throw ApiException.badRequest("no_start", "請先設定開始時間，再發布。");
        if (e.getBank() == null) throw ApiException.badRequest("no_bank", "這場考試的題庫已被刪除，請重新選擇題庫。");
        List<QuestionDto> questions = bankService.questionsOf(e.getBank().getId());
        if (questions.isEmpty()) throw ApiException.badRequest("empty_bank", "題庫裡還沒有題目，不能發布。");
        for (int i = 0; i < questions.size(); i++) {
            String issue = issueOf(questions.get(i));
            if (issue != null) {
                throw ApiException.badRequest("incomplete_question", "第 " + (i + 1) + " 題" + issue + "，請出題者補完後再發布。");
            }
        }
        e.setSnapshot(new ExamSnapshot(e.getBank().getName(), questions));
        e.setStatus(Exam.Status.PUBLISHED);
        e.setPublishedAt(Instant.now());
        audit.record("EXAM_PUBLISHED", "exam", e.getId(), e.getUnit().getId(), null, e.getName());
        return toDto(e, counts(List.of(id)).get(id), true);
    }

    /** 取消發布：只有還沒到開始時間才可以。 */
    @Transactional
    public ExamDto withdraw(Long id) {
        Exam e = findManaged(id);
        if (!"scheduled".equals(e.phase(Instant.now()))) {
            throw ApiException.conflict("not_scheduled", "只有已發布、還沒開始的考試可以取消發布。");
        }
        e.setStatus(Exam.Status.DRAFT);
        e.setSnapshot(null);
        e.setPublishedAt(null);
        audit.record("EXAM_WITHDRAWN", "exam", e.getId(), e.getUnit().getId(), null, e.getName());
        return toDto(e, counts(List.of(id)).get(id), true);
    }

    @Transactional
    public ExamDto end(Long id) {
        Exam e = findManaged(id);
        if (!"live".equals(e.phase(Instant.now()))) throw ApiException.conflict("not_live", "只有進行中的考試可以結束。");
        e.setStatus(Exam.Status.ENDED);
        e.setEndedAt(Instant.now());
        // 還在作答的人直接收卷
        attempts.closeAll(e.getId());
        audit.record("EXAM_ENDED", "exam", e.getId(), e.getUnit().getId(), null, e.getName());
        return toDto(e, counts(List.of(id)).get(id), true);
    }

    /** 封存：考試變成唯讀，考生不能再登入查成績。資料還在，可以取消封存。 */
    @Transactional
    public ExamDto archive(Long id) {
        Exam e = findManaged(id);
        if (e.getStatus() != Exam.Status.ENDED) throw ApiException.conflict("not_ended", "考試結束後才能封存。");
        if (e.isArchived()) throw ApiException.conflict("archived", "這場考試已經封存了。");
        e.setArchivedAt(Instant.now());
        audit.record("EXAM_ARCHIVED", "exam", e.getId(), e.getUnit().getId(), null, e.getName());
        return toDto(e, counts(List.of(id)).get(id), true);
    }

    @Transactional
    public ExamDto unarchive(Long id) {
        Exam e = findManaged(id);
        if (!e.isArchived()) throw ApiException.conflict("not_archived", "這場考試沒有封存。");
        e.setArchivedAt(null);
        audit.record("EXAM_UNARCHIVED", "exam", e.getId(), e.getUnit().getId(), null, e.getName());
        return toDto(e, counts(List.of(id)).get(id), true);
    }

    /**
     * 刪除。草稿可以直接刪；考過的考試要先封存，而且過了保存期限才能刪。
     * 刪除會連同考生名單、答卷、評分一起消失，無法復原；操作紀錄只留下考試名稱與人數。
     */
    @Transactional
    public void delete(Long id, String reason) {
        Exam e = findManaged(id);
        if (e.getStatus() == Exam.Status.DRAFT) {
            audit.record("EXAM_DELETED", "exam", e.getId(), e.getUnit().getId(), null, e.getName());
            examRepo.delete(e);
            return;
        }
        if (!e.isArchived()) throw ApiException.conflict("not_archived", "考過的考試要先封存，才能刪除。");
        Instant until = retainUntil(e);
        if (until == null) throw ApiException.conflict("not_ended", "考試結束後才能刪除。");
        if (Instant.now().isBefore(until)) {
            throw ApiException.conflict("retained", "這場考試的資料要保留到 "
                    + java.time.LocalDate.ofInstant(until.minusSeconds(1), TAIPEI) + "，之後才能刪除。");
        }
        if (reason == null || reason.isBlank()) throw ApiException.badRequest("no_reason", "請填寫刪除的理由。");
        audit.record("EXAM_PURGED", "exam", e.getId(), e.getUnit().getId(), reason.trim(),
                e.getName() + "，考生 " + candidateRepo.countByExamId(id) + " 位");
        examRepo.delete(e);
    }

    /** 保存期限：考試結束那一年 + N 年的年底（台北時間）。還沒結束的考試沒有期限。 */
    Instant retainUntil(Exam e) {
        if (e.getEndedAt() == null) return null;
        int year = java.time.LocalDate.ofInstant(e.getEndedAt(), TAIPEI).getYear() + props.retention().years();
        return java.time.LocalDate.of(year + 1, 1, 1).atStartOfDay(TAIPEI).toInstant();
    }

    // ── 緊急檢視（系統管理員）────────────────────────────
    // 這兩個方法不檢查單位角色。呼叫端（EmergencyAccessService）必須已經確認授權有效並寫入操作紀錄。

    @Transactional(readOnly = true)
    public ExamDto getForEmergency(Long id) {
        Exam e = examRepo.findById(id).orElseThrow(() -> ApiException.notFound("找不到這場考試。"));
        return toDto(e, counts(List.of(id)).get(id), false);
    }

    /** 名單與作答狀態；登入碼不給（拿到登入碼就能冒充考生）。 */
    @Transactional(readOnly = true)
    public List<CandidateDto> candidatesForEmergency(Long examId) {
        Instant now = Instant.now();
        return candidateRepo.findByExamIdOrderBySeatAscIdAsc(examId).stream().map(c -> {
            CandidateDto d = toDto(c, now, null);
            return new CandidateDto(d.id(), d.ticket(), d.name(), d.dept(), d.seat(), null, d.status(), d.locked(),
                    d.leaveCount(), null, 0);
        }).toList();
    }

    // ── 考生名單 ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CandidateDto> candidates(Long examId) {
        findVisible(examId);
        Instant now = Instant.now();
        var byCandidate = attempts.byCandidate(examId);
        return candidateRepo.findByExamIdOrderBySeatAscIdAsc(examId).stream()
                .map(c -> toDto(c, now, byCandidate.get(c.getId()))).toList();
    }

    /** 檢查上傳的名單，不寫入資料庫。 */
    @Transactional(readOnly = true)
    public RosterPreview preview(Long examId, String fileName, byte[] bytes) {
        Exam e = findManaged(examId);
        requireOpen(e);
        List<RosterParser.RawRow> raw = RosterParser.parse(fileName, bytes);
        if (raw.isEmpty()) throw ApiException.badRequest("empty_file", "檔案裡沒有資料。");

        // 找標題列：有「准考證」和「姓名」就照標題對欄位，否則當作 准考證號、姓名、科系 的順序
        int ticketCol = 0, nameCol = 1, deptCol = 2, first = 0;
        List<String> head = raw.get(0).cells();
        int t = indexOf(head, "准考證", "考生編號", "編號"), n = indexOf(head, "姓名", "名字");
        if (t >= 0 && n >= 0) {
            ticketCol = t;
            nameCol = n;
            deptCol = indexOf(head, "科系", "系所", "科別", "報考", "類組");
            first = 1;
        }
        Set<String> existing = new HashSet<>(candidateRepo.ticketsOf(examId));
        Set<String> seen = new HashSet<>();
        List<RosterRow> rows = new ArrayList<>();
        List<RosterProblem> problems = new ArrayList<>();
        for (RosterParser.RawRow r : raw.subList(first, raw.size())) {
            String ticket = normalizeTicket(cell(r.cells(), ticketCol));
            String name = cell(r.cells(), nameCol);
            String dept = deptCol < 0 ? "" : cell(r.cells(), deptCol);
            String who = ((ticket.isEmpty() ? "(空白)" : ticket) + " " + name).trim();
            String reason = null;
            if (ticket.isEmpty()) reason = "缺少准考證號";
            else if (name.isEmpty()) reason = "缺少姓名";
            else if (ticket.length() > 40) reason = "准考證號太長";
            else if (name.length() > 100 || dept.length() > 100) reason = "姓名或科系太長";
            else if (existing.contains(ticket)) reason = "已經在名單中";
            else if (!seen.add(ticket)) reason = "准考證號在檔案裡重複";
            if (reason != null) problems.add(new RosterProblem("第 " + r.row() + " 列", who, reason));
            else rows.add(new RosterRow(ticket, name, dept));
        }
        return new RosterPreview(fileName, rows, problems);
    }

    /** 匯入：全部合格才寫入，自動排座位並產生登入碼。 */
    @Transactional
    public ImportResult importRows(Long examId, List<RosterRow> rows) {
        Exam e = findManaged(examId);
        requireOpen(e);
        Set<String> taken = new HashSet<>(candidateRepo.ticketsOf(examId));
        int seat = candidateRepo.maxSeat(examId);
        List<Candidate> created = new ArrayList<>();
        for (RosterRow r : rows) {
            String ticket = normalizeTicket(r.ticket());
            if (!taken.add(ticket)) {
                throw ApiException.conflict("duplicate_ticket", "准考證號 " + ticket + " 重複了，名單沒有匯入。請重新上傳檢查。");
            }
            created.add(new Candidate(e, ticket, r.name().trim(), r.dept() == null ? "" : r.dept().trim(), ++seat,
                    randomCode()));
        }
        candidateRepo.saveAll(created);
        audit.record("CANDIDATES_IMPORTED", "exam", examId, e.getUnit().getId(), null, created.size() + " 位");
        return new ImportResult(created.size(), candidateRepo.countByExamId(examId));
    }

    @Transactional
    public String resetCode(Long examId, Long candidateId) {
        Exam e = findVisible(examId);
        Candidate c = findCandidate(examId, candidateId);
        if (e.isArchived()) throw ApiException.conflict("archived", "這場考試已經封存，不能再更動。");
        if (c.getStatus() == Candidate.Status.DONE) throw ApiException.conflict("done", "這位考生已經交卷了。");
        c.setLoginCode(randomCode());
        c.setFailedLogins(0);
        c.setLockedUntil(null);
        audit.record("CANDIDATE_CODE_RESET", "candidate", c.getId(), e.getUnit().getId(), null, c.getTicket());
        return c.getLoginCode();
    }

    /** 監考補時：考生因設備問題損失時間時，把截止時間往後延。 */
    @Transactional
    public CandidateDto extendTime(Long examId, Long candidateId, ExamDtos.ExtendRequest req) {
        Exam e = findVisible(examId);
        Candidate c = findCandidate(examId, candidateId);
        if (e.isArchived()) throw ApiException.conflict("archived", "這場考試已經封存，不能再更動。");
        if (e.getStatus() != Exam.Status.PUBLISHED) throw ApiException.conflict("ended", "考試不在進行中，不能補時。");
        var a = attempts.extend(candidateId, req.minutes());
        audit.record("ATTEMPT_EXTENDED", "candidate", c.getId(), e.getUnit().getId(), req.reason().trim(),
                "%s 補時 %d 分鐘（累計 %d 分鐘）".formatted(c.getTicket(), req.minutes(), a.getExtraMinutes()));
        return toDto(c, Instant.now(), a);
    }

    @Transactional
    public void removeCandidate(Long examId, Long candidateId) {
        Exam e = findManaged(examId);
        Candidate c = findCandidate(examId, candidateId);
        if (c.getStatus() != Candidate.Status.IDLE) {
            throw ApiException.conflict("started", "這位考生已經開始作答，不能從名單移除。");
        }
        audit.record("CANDIDATE_REMOVED", "candidate", c.getId(), e.getUnit().getId(), null,
                c.getTicket() + " " + c.getName());
        candidateRepo.delete(c);
    }

    // ── 內部 ─────────────────────────────────────────────

    /** 負責人或監考看得到；其他人一律「找不到」。 */
    private Exam findVisible(Long id) {
        Exam e = examRepo.findById(id).orElseThrow(() -> ApiException.notFound("找不到這場考試。"));
        if (!access.hasUnitRole(e.getUnit().getId(), Role.OWNER, Role.PROCTOR)) {
            throw ApiException.notFound("找不到這場考試。");
        }
        return e;
    }

    private Exam findManaged(Long id) {
        Exam e = findVisible(id);
        access.requireUnitRole(e.getUnit().getId(), Role.OWNER);
        return e;
    }

    private Candidate findCandidate(Long examId, Long candidateId) {
        return candidateRepo.findById(candidateId).filter(c -> c.getExam().getId().equals(examId))
                .orElseThrow(() -> ApiException.notFound("找不到這位考生。"));
    }

    private static void requireOpen(Exam e) {
        if (e.isArchived()) throw ApiException.conflict("archived", "這場考試已經封存，不能再更動。");
        if (e.getStatus() == Exam.Status.ENDED) throw ApiException.conflict("ended", "考試已經結束，不能再匯入考生。");
    }

    private Map<Long, long[]> counts(List<Long> examIds) {
        Map<Long, long[]> map = new HashMap<>();
        if (examIds.isEmpty()) return map;
        for (Object[] row : candidateRepo.countByStatus(examIds)) {
            long[] c = map.computeIfAbsent((Long) row[0], k -> new long[3]);
            c[((Candidate.Status) row[1]).ordinal()] = (Long) row[2];
        }
        return map;
    }

    private ExamDto toDto(Exam e, long[] counts, boolean canManage) {
        long[] c = counts == null ? new long[3] : counts;
        // 已發布的考試以快照為準；草稿沒有快照，題數在前端從題庫看
        ExamSnapshot snap = e.getSnapshot();
        int questionCount = snap == null ? 0 : snap.questions().size();
        BigDecimal total = snap == null ? BigDecimal.ZERO
                : snap.questions().stream().map(QuestionDto::points).reduce(BigDecimal.ZERO, BigDecimal::add);
        Bank bank = e.getBank();
        return new ExamDto(e.getId(), e.getUnit().getId(), e.getUnit().getName(), e.getName(),
                bank == null ? null : bank.getId(), snap != null ? snap.bankName() : bank == null ? null : bank.getName(),
                e.getStartAt(), e.getDuration(), e.getMode(), e.phase(Instant.now()), questionCount, total,
                c[0] + c[1] + c[2], c[1], c[2], canManage, e.getResultsPublishedAt(),
                e.getArchivedAt(), retainUntil(e));
    }

    private static CandidateDto toDto(Candidate c, Instant now, tw.testa.attempt.Attempt a) {
        return new CandidateDto(c.getId(), c.getTicket(), c.getName(), c.getDept(), c.getSeat(), c.getLoginCode(),
                c.getStatus(), c.getLockedUntil() != null && c.getLockedUntil().isAfter(now), c.getLeaveCount(),
                a == null || a.isSubmitted() ? null : a.getDeadlineAt(), a == null ? 0 : a.getExtraMinutes());
    }

    /** 這題還缺什麼（與前端 issuesOf 相同的規則）；null 代表可以出題。 */
    static String issueOf(QuestionDto q) {
        if (q.stem().isBlank()) return "還沒有輸入題目";
        if (q.type().equals("single") || q.type().equals("multiple")) {
            if (q.choices().stream().filter(c -> !c.text().isBlank()).count() < 2) return "至少需要兩個選項";
            if (q.correct().isEmpty()) return "還沒有設定正確答案";
        }
        if (q.type().equals("blank") && (q.blanks().isEmpty() || q.blanks().stream().anyMatch(b -> b.answers().isEmpty()))) {
            return "有空格還沒有設定答案";
        }
        return null;
    }

    static String normalizeTicket(String ticket) {
        return ticket == null ? "" : ticket.trim().toUpperCase();
    }

    static String randomCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String cell(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index).trim() : "";
    }

    private static int indexOf(List<String> head, String... keywords) {
        for (String k : keywords) {
            for (int i = 0; i < head.size(); i++) {
                if (head.get(i).contains(k)) return i;
            }
        }
        return -1;
    }
}
