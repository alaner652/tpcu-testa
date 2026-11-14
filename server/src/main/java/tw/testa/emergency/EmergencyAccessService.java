package tw.testa.emergency;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.common.ApiException;
import tw.testa.exam.Exam;
import tw.testa.exam.ExamDtos.CandidateDto;
import tw.testa.exam.ExamDtos.ExamDto;
import tw.testa.exam.ExamRepository;
import tw.testa.exam.ExamService;
import tw.testa.grading.GradingDtos.Results;
import tw.testa.grading.GradingService;
import tw.testa.unit.Role;

/**
 * Emergency access for system administrators (school decision, 2026-11-14). Administrators normally cannot see exams;
 * a unit owner can grant one administrator read-only access to one exam, with a reason and an expiry.
 * - Grants, revocations and every view are audited.
 * - Read-only: exam details, roster and progress (no login codes), results and grading progress. No edits, no questions or answers.
 */
@Service
public class EmergencyAccessService {
    public record AdminOption(Long staffId, String name, String email) {}

    /** active: not expired and not revoked. */
    public record Grant(Long id, Long examId, String examName, String unitName, Long staffId, String staffName,
            String grantedByName, String reason, Instant grantedAt, Instant expiresAt, Instant revokedAt, int viewCount,
            Instant lastViewedAt, boolean active) {}

    public record Overview(List<AdminOption> admins, List<Grant> grants) {}

    public record GrantRequest(@NotNull(message = "請選擇要授權的系統管理員。") Long staffId,
            @NotBlank(message = "請填寫授權的理由。") @Size(max = 500, message = "理由太長了。") String reason,
            @NotNull @Min(value = 1, message = "時效至少 1 小時。") @Max(value = 72, message = "時效最長 72 小時。") Integer hours) {}

    public record View(Grant grant, ExamDto exam, List<CandidateDto> candidates, Results results) {}

    private static final String SELECT = """
            select g.id, g.exam_id, e.name, u.name, g.staff_id, s.name, b.name, g.reason, g.granted_at, g.expires_at,
                   g.revoked_at, g.view_count, g.last_viewed_at
            from exam_emergency_access g
            join exam e on e.id = g.exam_id join unit u on u.id = e.unit_id
            join staff s on s.id = g.staff_id join staff b on b.id = g.granted_by
            """;

    private static final RowMapper<Grant> MAPPER = (rs, i) -> {
        Instant expires = rs.getTimestamp(10).toInstant();
        Instant revoked = rs.getTimestamp(11) == null ? null : rs.getTimestamp(11).toInstant();
        return new Grant(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant(), expires, revoked, rs.getInt(12),
                rs.getTimestamp(13) == null ? null : rs.getTimestamp(13).toInstant(),
                revoked == null && expires.isAfter(Instant.now()));
    };

    private final JdbcTemplate jdbc;
    private final ExamRepository examRepo;
    private final ExamService exams;
    private final GradingService grading;
    private final AccessGuard access;
    private final AuditService audit;

    public EmergencyAccessService(JdbcTemplate jdbc, ExamRepository examRepo, ExamService exams, GradingService grading,
            AccessGuard access, AuditService audit) {
        this.jdbc = jdbc;
        this.examRepo = examRepo;
        this.exams = exams;
        this.grading = grading;
        this.access = access;
        this.audit = audit;
    }

    // ── Unit owner ───────────────────────────────────────

    @Transactional(readOnly = true)
    public Overview overview(Long examId) {
        ownedExam(examId);
        List<AdminOption> admins = jdbc.query(
                "select id, name, email from staff where is_system_admin and active order by name, id",
                (rs, i) -> new AdminOption(rs.getLong(1), rs.getString(2), rs.getString(3)));
        return new Overview(admins, jdbc.query(SELECT + " where g.exam_id = ? order by g.id desc", MAPPER, examId));
    }

    @Transactional
    public Grant grant(Long examId, GrantRequest req) {
        Exam e = ownedExam(examId);
        Integer ok = jdbc.queryForObject("select count(*) from staff where id = ? and is_system_admin and active",
                Integer.class, req.staffId());
        if (ok == null || ok == 0) throw ApiException.badRequest("not_admin", "只能授權給啟用中的系統管理員。");
        Integer existing = jdbc.queryForObject("""
                select count(*) from exam_emergency_access
                where exam_id = ? and staff_id = ? and revoked_at is null and expires_at > now()""", Integer.class, examId,
                req.staffId());
        if (existing != null && existing > 0) {
            throw ApiException.conflict("already_granted", "這位系統管理員已經有一筆還在有效期內的授權。");
        }
        Instant expires = Instant.now().plus(Duration.ofHours(req.hours()));
        Long id = jdbc.queryForObject("""
                insert into exam_emergency_access (exam_id, staff_id, granted_by, reason, expires_at)
                values (?, ?, ?, ?, ?) returning id""", Long.class, examId, req.staffId(), access.current().id(),
                req.reason().trim(), java.sql.Timestamp.from(expires));
        audit.record("EMERGENCY_ACCESS_GRANTED", "exam", examId, e.getUnit().getId(), req.reason().trim(),
                e.getName() + "，授權給 staff " + req.staffId() + "，" + req.hours() + " 小時");
        return byId(id);
    }

    @Transactional
    public void revoke(Long examId, Long grantId) {
        Exam e = ownedExam(examId);
        int n = jdbc.update("""
                update exam_emergency_access set revoked_at = now()
                where id = ? and exam_id = ? and revoked_at is null and expires_at > now()""", grantId, examId);
        if (n == 0) throw ApiException.conflict("not_active", "這筆授權已經失效了。");
        audit.record("EMERGENCY_ACCESS_REVOKED", "exam", examId, e.getUnit().getId(), null, e.getName());
    }

    // ── System administrator ─────────────────────────────

    /** Active grants to me. */
    @Transactional(readOnly = true)
    public List<Grant> mine() {
        access.requireSystemAdmin();
        return jdbc.query(SELECT + """
                 where g.staff_id = ? and g.revoked_at is null and g.expires_at > now() order by g.expires_at""", MAPPER,
                access.current().id());
    }

    /** One view, one audit entry. A grant that is not mine, expired or revoked is "not found". */
    @Transactional
    public View view(Long grantId) {
        access.requireSystemAdmin();
        int n = jdbc.update("""
                update exam_emergency_access set view_count = view_count + 1, last_viewed_at = now()
                where id = ? and staff_id = ? and revoked_at is null and expires_at > now()""", grantId,
                access.current().id());
        if (n == 0) throw ApiException.notFound("找不到這筆授權，或它已經失效。");
        Grant g = byId(grantId);
        ExamDto exam = exams.getForEmergency(g.examId());
        audit.record("EMERGENCY_ACCESS_VIEWED", "exam", g.examId(), exam.unitId(), g.reason(), exam.name());
        return new View(g, exam, exams.candidatesForEmergency(g.examId()), grading.resultsForEmergency(g.examId()));
    }

    // ── Internal ─────────────────────────────────────────

    private Grant byId(Long id) {
        return jdbc.queryForObject(SELECT + " where g.id = ?", MAPPER, id);
    }

    private Exam ownedExam(Long examId) {
        Exam e = examRepo.findById(examId).orElseThrow(() -> ApiException.notFound("找不到這場考試。"));
        if (!access.hasUnitRole(e.getUnit().getId(), Role.OWNER)) throw ApiException.notFound("找不到這場考試。");
        return e;
    }
}
