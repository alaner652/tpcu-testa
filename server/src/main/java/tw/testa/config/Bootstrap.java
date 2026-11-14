package tw.testa.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import tw.testa.bank.BankDtos.BlankDto;
import tw.testa.bank.BankDtos.ChoiceDto;
import tw.testa.bank.BankDtos.QuestionDto;
import tw.testa.exam.Candidate;
import tw.testa.exam.CandidateRepository;
import tw.testa.exam.Exam;
import tw.testa.exam.ExamRepository;
import tw.testa.exam.ExamSnapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;
import tw.testa.unit.Role;
import tw.testa.unit.Unit;
import tw.testa.unit.UnitRepository;
import tw.testa.unit.UnitRole;
import tw.testa.unit.UnitRoleRepository;
import tw.testa.unit.UnitType;

/**
 * Demo data for the dev profile (testa.dev-seed=true): a system administrator, units, accounts and a live exam.
 * Production has no seed; the first administrator is created through first-run setup (SetupService).
 */
@Component
public class Bootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    private final TestaProperties props;
    private final StaffRepository staffRepo;
    private final UnitRepository unitRepo;
    private final UnitRoleRepository roleRepo;
    private final PasswordEncoder encoder;
    private final ExamRepository examRepo;
    private final CandidateRepository candidateRepo;

    public Bootstrap(TestaProperties props, StaffRepository staffRepo, UnitRepository unitRepo,
            UnitRoleRepository roleRepo, PasswordEncoder encoder, ExamRepository examRepo,
            CandidateRepository candidateRepo) {
        this.examRepo = examRepo;
        this.candidateRepo = candidateRepo;
        this.props = props;
        this.staffRepo = staffRepo;
        this.unitRepo = unitRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.devSeed()) return;
        if (!staffRepo.existsBySystemAdminTrue()) {
            staffRepo.save(new Staff("admin@testa.tw", encoder.encode("testa-admin"), "陳主任", true));
        }
        seedDemo();
        seedDemoExam();
    }

    /** Demo exam: published and live, candidate A115-0001 / 482915 can sign in right away (demo button on the login page). */
    private void seedDemoExam() {
        if (candidateRepo.findByTicketWithExam("A115-0001").stream().anyMatch(c -> c.getLoginCode().equals("482915"))) return;
        Unit unit = unitRepo.findAll().stream().filter(u -> u.getName().equals("招生組")).findFirst().orElse(null);
        Staff officer = staffRepo.findByEmail("officer@testa.tw").orElse(null);
        if (unit == null || officer == null) return;
        List<QuestionDto> questions = List.of(
                new QuestionDto("demo_q1", "single", "若 $f(x)=x^2-4x+3$，則 $f(x)$ 的最小值為何？", new BigDecimal("5"),
                        "配方得 $f(x)=(x-2)^2-1$。", List.of(new ChoiceDto("a", "$-1$"), new ChoiceDto("b", "$0$"),
                                new ChoiceDto("c", "$1$"), new ChoiceDto("d", "$3$")),
                        List.of("a"), List.of(), false, "", null, false, false),
                new QuestionDto("demo_q2", "multiple", "下列哪些數是無理數？", new BigDecimal("6"), "",
                        List.of(new ChoiceDto("a", "$\\sqrt{2}$"), new ChoiceDto("b", "$\\dfrac{22}{7}$"),
                                new ChoiceDto("c", "$\\pi$"), new ChoiceDto("d", "$\\sqrt{16}$")),
                        List.of("a", "c"), List.of(), false, "", null, false, false),
                new QuestionDto("demo_q3", "blank", "等差數列首項 $a_1=3$、公差 $d=4$，則 $a_{10}=$ ＿＿，前 10 項的和 $S_{10}=$ ＿＿。",
                        new BigDecimal("6"), "", List.of(), List.of(),
                        List.of(new BlankDto("b1", List.of("39")), new BlankDto("b2", List.of("210"))), false, "", null,
                        false, false),
                new QuestionDto("demo_q4", "essay", "試以數學歸納法證明：對任意正整數 $n$，$1+2+\\cdots+n=\\dfrac{n(n+1)}{2}$。",
                        new BigDecimal("10"), "", List.of(), List.of(), List.of(), false,
                        "驗證 n = 1（2 分）\n歸納假設（3 分）\n完成推導（4 分）\n結論（1 分）", 600, false, false));
        Exam exam = new Exam(unit, null, officer, "示範考試（開發用）", 60, Exam.Mode.CLASSROOM);
        exam.setStartAt(Instant.now());
        exam.setSnapshot(new ExamSnapshot("示範題庫", questions));
        exam.setStatus(Exam.Status.PUBLISHED);
        exam.setPublishedAt(Instant.now());
        examRepo.save(exam);
        candidateRepo.save(new Candidate(exam, "A115-0001", "王小明", "資訊工程科", 1, "482915"));
        candidateRepo.save(new Candidate(exam, "A115-0002", "李宜華", "電機工程科", 2, "135790"));
        log.info("Created demo exam: candidate A115-0001, login code 482915");
    }

    private void seedDemo() {
        if (unitRepo.count() > 0) return;
        Unit admission = unitRepo.save(new Unit("招生組", UnitType.ADMISSION));
        Staff officer = staffRepo.save(new Staff("officer@testa.tw", encoder.encode("testa-officer"), "王承辦", false));
        Staff teacher = staffRepo.save(new Staff("teacher@testa.tw", encoder.encode("testa-teacher"), "林老師", false));
        Staff teacher2 = staffRepo.save(new Staff("teacher2@testa.tw", encoder.encode("testa-teacher"), "張老師", false));
        Staff teacher3 = staffRepo.save(new Staff("teacher3@testa.tw", encoder.encode("testa-teacher"), "李老師", false));
        // MVP combined roles: officer = owner + proctor, teacher = author + grader
        roleRepo.save(new UnitRole(admission, officer, Role.OWNER));
        roleRepo.save(new UnitRole(admission, officer, Role.PROCTOR));
        for (Staff t : List.of(teacher, teacher2, teacher3)) {
            roleRepo.save(new UnitRole(admission, t, Role.AUTHOR));
            roleRepo.save(new UnitRole(admission, t, Role.GRADER));
        }
        log.info("Created demo data: one unit and the accounts officer@, teacher@, teacher2@ and teacher3@testa.tw");
    }
}
