package tw.testa.staff;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.common.ApiException;
import tw.testa.unit.MembershipService;
import tw.testa.unit.Role;
import tw.testa.unit.Unit;
import tw.testa.unit.UnitRepository;
import tw.testa.unit.UnitRole;
import tw.testa.unit.UnitRoleRepository;

/** Staff account management for system administrators. */
@RestController
@RequestMapping("/api/admin/staff")
@PreAuthorize("@access.isSystemAdmin()")
public class StaffAdminController {
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffRepository staffRepo;
    private final UnitRoleRepository roleRepo;
    private final UnitRepository unitRepo;
    private final MembershipService memberships;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AccessGuard access;
    private final JdbcTemplate jdbc;

    public StaffAdminController(StaffRepository staffRepo, UnitRoleRepository roleRepo, UnitRepository unitRepo,
            MembershipService memberships, PasswordEncoder encoder, AuditService audit, AccessGuard access,
            JdbcTemplate jdbc) {
        this.staffRepo = staffRepo;
        this.roleRepo = roleRepo;
        this.unitRepo = unitRepo;
        this.memberships = memberships;
        this.encoder = encoder;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    /** {@code id} is the role assignment, used to remove it through the unit's member API. */
    public record UnitRoleDto(Long id, Long unitId, String unitName, Role role) {}

    public record StaffDto(Long id, String email, String name, boolean systemAdmin, boolean active,
            boolean mustChangePassword, boolean locked, Instant lastLoginAt, Instant createdAt,
            List<UnitRoleDto> roles) {}

    /** Roles to grant in one unit right after the account is created. */
    public record Membership(@NotNull(message = "請選擇單位。") Long unitId,
            @NotEmpty(message = "請選擇角色。") List<@NotNull Role> roles) {}

    public record CreateRequest(
            @NotBlank(message = "請輸入 Email。") @Email(message = "Email 格式不正確。") @Size(max = 254) String email,
            @NotBlank(message = "請輸入姓名。") @Size(max = 100, message = "姓名太長了。") String name,
            Boolean systemAdmin, List<@Valid Membership> memberships) {
        boolean admin() { return Boolean.TRUE.equals(systemAdmin); }
    }

    public record Person(
            @NotBlank(message = "請輸入 Email。") @Email(message = "Email 格式不正確。") @Size(max = 254) String email,
            @NotBlank(message = "請輸入姓名。") @Size(max = 100, message = "姓名太長了。") String name) {}

    /** Several accounts at once, all with the same unit roles. Nothing is created unless every row is valid. */
    public record BatchRequest(
            @NotEmpty(message = "請至少輸入一位教職員。") @Size(max = 200, message = "一次最多建立 200 個帳號。") List<@Valid Person> people,
            List<@Valid Membership> memberships) {}

    /** After creating an account or resetting a password, the temporary password appears only in this response. */
    public record CreatedDto(StaffDto staff, String temporaryPassword) {}

    public record DeletionImpact(boolean deletable, long banks, long exams, long graderAssignments,
            long gradings, long emergencyAccesses) {}

    /** Several accounts at once. All succeed or none do. */
    public record StaffIds(
            @NotEmpty(message = "請至少選擇一個帳號。") @Size(max = 500, message = "一次最多處理 500 個帳號。") List<@NotNull Long> ids) {}

    public record BatchActiveRequest(
            @NotEmpty(message = "請至少選擇一個帳號。") @Size(max = 500, message = "一次最多處理 500 個帳號。") List<@NotNull Long> ids,
            @NotNull Boolean active) {}

    public record BatchMembershipRequest(
            @NotEmpty(message = "請至少選擇一個帳號。") @Size(max = 500, message = "一次最多處理 500 個帳號。") List<@NotNull Long> ids,
            @NotNull(message = "請選擇單位。") Long unitId,
            @NotEmpty(message = "請選擇角色。") List<@NotNull Role> roles) {}

    public record UpdateRequest(@Size(min = 1, max = 100, message = "請輸入姓名。") String name, Boolean active,
            Boolean systemAdmin) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<StaffDto> list() {
        Map<Long, List<UnitRoleDto>> byStaff = new HashMap<>();
        for (UnitRole r : roleRepo.findAllWithUnitAndStaff()) {
            byStaff.computeIfAbsent(r.getStaff().getId(), k -> new ArrayList<>()).add(toDto(r));
        }
        return staffRepo.findAll(org.springframework.data.domain.Sort.by("name", "id")).stream()
                .map(s -> toDto(s, byStaff.getOrDefault(s.getId(), List.of()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CreatedDto create(@Valid @RequestBody CreateRequest body) {
        String email = Staff.normalizeEmail(body.email());
        if (staffRepo.existsByEmail(email)) {
            throw ApiException.conflict("email_taken", "這個 Email 已經有帳號了。");
        }
        List<Unit> units = units(body.memberships());
        return createOne(email, body.name(), body.admin(), units, body.memberships());
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public List<CreatedDto> createBatch(@Valid @RequestBody BatchRequest body) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        List<String> emails = body.people().stream().map(p -> Staff.normalizeEmail(p.email())).toList();
        Set<String> taken = new HashSet<>();
        staffRepo.findByEmailIn(emails).forEach(s -> taken.add(s.getEmail()));
        for (int i = 0; i < emails.size(); i++) {
            String email = emails.get(i);
            if (!seen.add(email)) errors.put("people[" + i + "].email", "這個 Email 在名單裡重複了。");
            else if (taken.contains(email)) errors.put("people[" + i + "].email", "這個 Email 已經有帳號了。");
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "batch_invalid",
                    "有 " + errors.size() + " 筆資料不能建立，請修正後再試一次。", errors);
        }
        List<Unit> units = units(body.memberships());
        List<CreatedDto> created = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            created.add(createOne(emails.get(i), body.people().get(i).name(), false, units, body.memberships()));
        }
        return created;
    }

    private CreatedDto createOne(String email, String name, boolean admin, List<Unit> units, List<Membership> requested) {
        String password = randomPassword();
        Staff s = new Staff(email, encoder.encode(password), name.trim(), admin);
        s.setMustChangePassword(true);
        staffRepo.save(s);
        audit.record("STAFF_CREATED", "staff", s.getId(), null, null, admin ? "system_admin" : null);
        List<UnitRoleDto> roles = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            memberships.grant(units.get(i), s, requested.get(i).roles()).forEach(r -> roles.add(toDto(r)));
        }
        return new CreatedDto(toDto(s, roles), password);
    }

    /** Looks up every requested unit before anything is written. */
    private List<Unit> units(List<Membership> requested) {
        if (requested == null) return List.of();
        return requested.stream().map(m -> unitRepo.findById(m.unitId())
                .orElseThrow(() -> ApiException.notFound("找不到這個單位。"))).toList();
    }

    @PatchMapping("/{id}")
    @Transactional
    public StaffDto update(@PathVariable Long id, @RequestBody @Valid UpdateRequest body) {
        Staff s = find(id);
        boolean self = s.getId().equals(access.current().id());
        if (body.name() != null) s.setName(body.name().trim());
        if (body.active() != null && body.active() != s.isActive()) {
            if (self) throw ApiException.badRequest("self", "不能停用自己的帳號。");
            s.setActive(body.active());
            s.invalidateSessions();
            audit.record(body.active() ? "STAFF_ENABLED" : "STAFF_DISABLED", "staff", s.getId());
        }
        if (body.systemAdmin() != null && body.systemAdmin() != s.isSystemAdmin()) {
            if (self) throw ApiException.badRequest("self", "不能移除自己的系統管理員身分。");
            s.setSystemAdmin(body.systemAdmin());
            s.invalidateSessions();
            audit.record(body.systemAdmin() ? "SYSTEM_ADMIN_GRANTED" : "SYSTEM_ADMIN_REVOKED", "staff", s.getId());
        }
        return toDto(s, roleRepo.findByStaffIdWithUnit(id).stream().map(StaffAdminController::toDto).toList());
    }

    /** Resets the password and clears the lockout. Every device is signed out and the next sign-in must change the password. */
    @PostMapping("/{id}/reset-password")
    @Transactional
    public CreatedDto resetPassword(@PathVariable Long id) {
        Staff s = find(id);
        if (s.getId().equals(access.current().id())) {
            throw ApiException.badRequest("self", "要改自己的密碼，請用「變更密碼」。");
        }
        String password = randomPassword();
        s.setPasswordHash(encoder.encode(password));
        s.setMustChangePassword(true);
        s.setFailedLogins(0);
        s.setLockedUntil(null);
        s.invalidateSessions();
        audit.record("PASSWORD_RESET", "staff", s.getId());
        return new CreatedDto(toDto(s, List.of()), password);
    }

    @PostMapping("/batch/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void setActive(@Valid @RequestBody BatchActiveRequest body) {
        UpdateRequest change = new UpdateRequest(null, body.active(), null);
        forEach(body.ids(), s -> update(s.getId(), change));
    }

    @PostMapping("/batch/reset-password")
    @Transactional
    public List<CreatedDto> resetPasswords(@Valid @RequestBody StaffIds body) {
        List<CreatedDto> issued = new ArrayList<>();
        forEach(body.ids(), s -> issued.add(resetPassword(s.getId())));
        return issued;
    }

    @PostMapping("/batch/memberships")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void addToUnit(@Valid @RequestBody BatchMembershipRequest body) {
        Unit unit = unitRepo.findById(body.unitId()).orElseThrow(() -> ApiException.notFound("找不到這個單位。"));
        forEach(body.ids(), s -> memberships.grant(unit, s, body.roles()));
    }

    /** Runs the single-account action for each id; an error names the account it stopped at. */
    private void forEach(List<Long> ids, Consumer<Staff> action) {
        for (Long id : new LinkedHashSet<>(ids)) {
            Staff s = find(id);
            try {
                action.accept(s);
            } catch (ApiException e) {
                throw new ApiException(e.status(), e.code(), s.getName() + "：" + e.getMessage() + " 這次的操作都沒有生效。");
            }
        }
    }

    /** Only unused, already-disabled accounts can be permanently deleted; historical work must keep its author. */
    @GetMapping("/{id}/deletion-impact")
    @Transactional(readOnly = true)
    public DeletionImpact deletionImpact(@PathVariable Long id) {
        find(id);
        return impact(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Staff s = find(id);
        if (s.getId().equals(access.current().id())) {
            throw ApiException.badRequest("self", "不能刪除自己的帳號。");
        }
        if (s.isActive()) {
            throw ApiException.conflict("active_account", "請先停用帳號，再永久刪除。");
        }
        if (!impact(id).deletable()) {
            throw ApiException.conflict("staff_in_use", "這個帳號已有題庫、考試、評分或緊急授權紀錄。為了保留歷史資料，不能永久刪除，請維持停用即可。");
        }
        audit.record("STAFF_DELETED", "staff", s.getId(), null, null, s.getEmail());
        staffRepo.delete(s);
        staffRepo.flush();
    }

    private DeletionImpact impact(Long id) {
        long banks = count("select count(*) from bank where created_by = ?", id);
        long exams = count("select count(*) from exam where created_by = ?", id);
        long graderAssignments = count("select count(*) from exam_grader where staff_id = ?", id);
        long gradings = count("select count(*) from grading where staff_id = ?", id);
        long emergencyAccesses = count("select count(*) from exam_emergency_access where staff_id = ? or granted_by = ?", id, id);
        boolean deletable = banks + exams + graderAssignments + gradings + emergencyAccesses == 0;
        return new DeletionImpact(deletable, banks, exams, graderAssignments, gradings, emergencyAccesses);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Staff find(Long id) {
        return staffRepo.findById(id).orElseThrow(() -> ApiException.notFound("找不到這個帳號。"));
    }

    private static StaffDto toDto(Staff s, List<UnitRoleDto> roles) {
        return new StaffDto(s.getId(), s.getEmail(), s.getName(), s.isSystemAdmin(), s.isActive(),
                s.isMustChangePassword(), s.isLocked(Instant.now()), s.getLastLoginAt(), s.getCreatedAt(), roles);
    }

    private static UnitRoleDto toDto(UnitRole r) {
        return new UnitRoleDto(r.getId(), r.getUnit().getId(), r.getUnit().getName(), r.getRole());
    }

    static String randomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }
}
