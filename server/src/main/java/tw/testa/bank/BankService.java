package tw.testa.bank;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.auth.StaffPrincipal;
import tw.testa.bank.BankDtos.BankDto;
import tw.testa.bank.BankDtos.BlankDto;
import tw.testa.bank.BankDtos.ChoiceDto;
import tw.testa.bank.BankDtos.CreateBankRequest;
import tw.testa.bank.BankDtos.QuestionDto;
import tw.testa.bank.BankDtos.SaveBankRequest;
import tw.testa.common.ApiException;
import tw.testa.staff.StaffRepository;
import tw.testa.unit.Role;
import tw.testa.unit.Unit;
import tw.testa.unit.UnitRepository;
import tw.testa.unit.UnitRole;
import tw.testa.unit.UnitRoleRepository;

/**
 * Question banks. Permissions:
 * - Authors can view, create, edit and delete their unit's banks.
 * - Owners can only view them (they pick a bank when creating an exam).
 * - Proctors, graders and system administrators cannot see banks.
 */
@Service
public class BankService {
    private final BankRepository bankRepo;
    private final QuestionRepository questionRepo;
    private final UnitRepository unitRepo;
    private final UnitRoleRepository roleRepo;
    private final StaffRepository staffRepo;
    private final AccessGuard access;
    private final AuditService audit;

    public BankService(BankRepository bankRepo, QuestionRepository questionRepo, UnitRepository unitRepo,
            UnitRoleRepository roleRepo, StaffRepository staffRepo, AccessGuard access, AuditService audit) {
        this.bankRepo = bankRepo;
        this.questionRepo = questionRepo;
        this.unitRepo = unitRepo;
        this.roleRepo = roleRepo;
        this.staffRepo = staffRepo;
        this.access = access;
        this.audit = audit;
    }

    /** All banks visible to me, with questions. */
    @Transactional(readOnly = true)
    public List<BankDto> list() {
        StaffPrincipal me = access.current();
        // unit -> editable
        Map<Long, Boolean> units = new HashMap<>();
        for (UnitRole r : roleRepo.findByStaffIdWithUnit(me.id())) {
            if (!r.getUnit().isActive()) continue;
            if (r.getRole() == Role.AUTHOR) units.put(r.getUnit().getId(), true);
            else if (r.getRole() == Role.OWNER) units.putIfAbsent(r.getUnit().getId(), false);
        }
        if (units.isEmpty()) return List.of();
        List<Bank> banks = bankRepo.findByUnitIds(units.keySet());
        Map<Long, List<Question>> byBank = new LinkedHashMap<>();
        if (!banks.isEmpty()) {
            for (Question q : questionRepo.findByBankIds(banks.stream().map(Bank::getId).toList())) {
                byBank.computeIfAbsent(q.getBank().getId(), k -> new ArrayList<>()).add(q);
            }
        }
        return banks.stream()
                .map(b -> toDto(b, byBank.getOrDefault(b.getId(), List.of()), units.get(b.getUnit().getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BankDto get(Long id) {
        Bank b = find(id);
        boolean editable = access.hasUnitRole(b.getUnit().getId(), Role.AUTHOR);
        if (!editable) access.requireUnitRole(b.getUnit().getId(), Role.OWNER);
        return toDto(b, questionRepo.findByBankIdOrderByPositionAscIdAsc(id), editable);
    }

    @Transactional
    public BankDto create(CreateBankRequest req) {
        access.requireUnitRole(req.unitId(), Role.AUTHOR);
        Unit unit = unitRepo.findById(req.unitId()).orElseThrow(() -> ApiException.notFound("找不到這個單位。"));
        Bank b = new Bank(unit, staffRepo.getReferenceById(access.current().id()));
        b.setName(req.name() == null ? "未命名題庫" : req.name().trim());
        b.setSubject(req.subject() == null ? "未分類" : req.subject().trim());
        if (req.duration() != null) b.setDuration(req.duration());
        bankRepo.save(b);
        List<Question> saved = syncQuestions(b, List.of(), req.questions() == null ? List.of() : req.questions());
        audit.record("BANK_CREATED", "bank", b.getId(), unit.getId(), null, b.getName());
        return toDto(b, saved, true);
    }

    /** Saves the whole bank: the submitted question list wins (create, update, delete, reorder). */
    @Transactional
    public BankDto save(Long id, SaveBankRequest req) {
        Bank b = bankRepo.findForUpdate(id).orElseThrow(() -> ApiException.notFound("找不到這份題庫，可能已經被刪除。"));
        access.requireUnitRole(b.getUnit().getId(), Role.AUTHOR);
        if (b.getVersion() != req.version()) {
            throw ApiException.conflict("stale_version", "這份題庫剛剛被其他人修改過。請重新整理頁面，再繼續編輯。");
        }
        b.setName(req.name().trim());
        b.setSubject(req.subject().trim());
        b.setDuration(req.duration());
        b.touch();
        List<Question> saved = syncQuestions(b, questionRepo.findByBankIdOrderByPositionAscIdAsc(id), req.questions());
        return toDto(b, saved, true);
    }

    @Transactional
    public void delete(Long id) {
        Bank b = find(id);
        access.requireUnitRole(b.getUnit().getId(), Role.AUTHOR);
        audit.record("BANK_DELETED", "bank", b.getId(), b.getUnit().getId(), null, b.getName());
        bankRepo.delete(b);
    }

    /** Internal (freezing questions on publish); the caller checks permissions. */
    @Transactional(readOnly = true)
    public List<QuestionDto> questionsOf(Long bankId) {
        return questionRepo.findByBankIdOrderByPositionAscIdAsc(bankId).stream().map(BankService::toDto).toList();
    }

    private Bank find(Long id) {
        // Without permission it is "not found", so existence is not revealed
        Bank b = bankRepo.findById(id).orElseThrow(() -> ApiException.notFound("找不到這份題庫。"));
        if (!access.hasUnitRole(b.getUnit().getId(), Role.AUTHOR, Role.OWNER)) {
            throw ApiException.notFound("找不到這份題庫。");
        }
        return b;
    }

    private List<Question> syncQuestions(Bank bank, List<Question> existing, List<QuestionDto> incoming) {
        Set<String> seen = new HashSet<>();
        for (QuestionDto q : incoming) {
            if (!seen.add(q.id())) throw ApiException.badRequest("duplicate_question", "題目代號重複了。");
            validate(q);
        }
        Map<String, Question> byClientId = new HashMap<>();
        for (Question q : existing) byClientId.put(q.getClientId(), q);

        List<Question> removed = existing.stream().filter(q -> !seen.contains(q.getClientId())).toList();
        if (!removed.isEmpty()) {
            questionRepo.deleteAll(removed);
            questionRepo.flush();
        }
        List<Question> result = new ArrayList<>(incoming.size());
        int position = 0;
        for (QuestionDto dto : incoming) {
            Question q = byClientId.get(dto.id());
            if (q == null) q = new Question(bank, dto.id());
            q.setPosition(position++);
            q.setType(dto.type());
            q.setStem(dto.stem());
            q.setPoints(dto.points().setScale(1, RoundingMode.HALF_UP));
            q.setContent(new QuestionContent(dto.explanation(),
                    dto.choices().stream().map(c -> new QuestionContent.Choice(c.id(), c.text())).toList(),
                    List.copyOf(dto.correct()),
                    dto.blanks().stream().map(b -> new QuestionContent.Blank(b.id(), List.copyOf(b.answers()))).toList(),
                    dto.caseSensitive(), dto.rubric(), dto.wordLimit(), dto.aiGrading(), dto.review()));
            result.add(questionRepo.save(q));
        }
        return result;
    }

    /** Rejects only contradictory data; unfinished questions (no stem, no answer) are valid drafts. */
    private static void validate(QuestionDto q) {
        Set<String> choiceIds = new HashSet<>();
        for (ChoiceDto c : q.choices()) {
            if (!choiceIds.add(c.id())) throw ApiException.badRequest("duplicate_choice", "選項代號重複了。");
        }
        if (!choiceIds.containsAll(q.correct())) {
            throw ApiException.badRequest("unknown_choice", "正確答案指向不存在的選項。");
        }
        Set<String> blankIds = new HashSet<>();
        for (BlankDto b : q.blanks()) {
            if (!blankIds.add(b.id())) throw ApiException.badRequest("duplicate_blank", "空格代號重複了。");
        }
    }

    private static BankDto toDto(Bank b, List<Question> questions, boolean editable) {
        return new BankDto(b.getId(), b.getUnit().getId(), b.getUnit().getName(), b.getName(), b.getSubject(),
                b.getDuration(), b.getVersion(), editable, b.getUpdatedAt(),
                questions.stream().map(BankService::toDto).toList());
    }

    private static QuestionDto toDto(Question q) {
        QuestionContent c = q.getContent();
        return new QuestionDto(q.getClientId(), q.getType(), q.getStem(), q.getPoints(), c.explanation(),
                c.choices().stream().map(x -> new ChoiceDto(x.id(), x.text())).toList(), c.correct(),
                c.blanks().stream().map(x -> new BlankDto(x.id(), x.answers())).toList(), c.caseSensitive(),
                c.rubric(), c.wordLimit(), c.aiGrading(), c.review());
    }
}
