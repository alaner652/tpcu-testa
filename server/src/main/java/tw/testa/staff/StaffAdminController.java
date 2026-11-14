package tw.testa.staff;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
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
import jakarta.validation.constraints.Size;
import tw.testa.audit.AuditService;
import tw.testa.auth.AccessGuard;
import tw.testa.common.ApiException;
import tw.testa.unit.Role;
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
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AccessGuard access;

    public StaffAdminController(StaffRepository staffRepo, UnitRoleRepository roleRepo, PasswordEncoder encoder,
            AuditService audit, AccessGuard access) {
        this.staffRepo = staffRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.audit = audit;
        this.access = access;
    }

    public record UnitRoleDto(Long unitId, String unitName, Role role) {}

    public record StaffDto(Long id, String email, String name, boolean systemAdmin, boolean active,
            boolean mustChangePassword, boolean locked, Instant lastLoginAt, Instant createdAt,
            List<UnitRoleDto> roles) {}

    public record CreateRequest(
            @NotBlank(message = "請輸入 Email。") @Email(message = "Email 格式不正確。") @Size(max = 254) String email,
            @NotBlank(message = "請輸入姓名。") @Size(max = 100, message = "姓名太長了。") String name,
            Boolean systemAdmin) {
        boolean admin() { return Boolean.TRUE.equals(systemAdmin); }
    }

    /** After creating an account or resetting a password, the temporary password appears only in this response. */
    public record CreatedDto(StaffDto staff, String temporaryPassword) {}

    public record UpdateRequest(@Size(min = 1, max = 100, message = "請輸入姓名。") String name, Boolean active,
            Boolean systemAdmin) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<StaffDto> list() {
        Map<Long, List<UnitRoleDto>> byStaff = new HashMap<>();
        for (UnitRole r : roleRepo.findAllWithUnitAndStaff()) {
            byStaff.computeIfAbsent(r.getStaff().getId(), k -> new ArrayList<>())
                    .add(new UnitRoleDto(r.getUnit().getId(), r.getUnit().getName(), r.getRole()));
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
        String password = randomPassword();
        Staff s = new Staff(email, encoder.encode(password), body.name().trim(), body.admin());
        s.setMustChangePassword(true);
        staffRepo.save(s);
        audit.record("STAFF_CREATED", "staff", s.getId(), null, null, body.admin() ? "system_admin" : null);
        return new CreatedDto(toDto(s, List.of()), password);
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
        return toDto(s, roleRepo.findByStaffIdWithUnit(id).stream()
                .map(r -> new UnitRoleDto(r.getUnit().getId(), r.getUnit().getName(), r.getRole())).toList());
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

    private Staff find(Long id) {
        return staffRepo.findById(id).orElseThrow(() -> ApiException.notFound("找不到這個帳號。"));
    }

    private static StaffDto toDto(Staff s, List<UnitRoleDto> roles) {
        return new StaffDto(s.getId(), s.getEmail(), s.getName(), s.isSystemAdmin(), s.isActive(),
                s.isMustChangePassword(), s.isLocked(Instant.now()), s.getLastLoginAt(), s.getCreatedAt(), roles);
    }

    static String randomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }
}
