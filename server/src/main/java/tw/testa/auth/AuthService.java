package tw.testa.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.audit.AuditService;
import tw.testa.common.ApiException;
import tw.testa.config.TestaProperties;
import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;
import tw.testa.unit.Role;
import tw.testa.unit.UnitRole;
import tw.testa.unit.UnitRoleRepository;
import tw.testa.unit.UnitType;

@Service
public class AuthService {
    public static final int MIN_PASSWORD = 8;
    /** BCrypt only reads the first 72 bytes; reject longer passwords so trailing characters cannot be ignored. */
    public static final int MAX_PASSWORD_BYTES = 72;

    private static final String BAD_CREDENTIALS = "帳號或密碼不正確，請再試一次。";

    private final StaffRepository staffRepo;
    private final UnitRoleRepository roleRepo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final TestaProperties props;
    /** Hash even when the account does not exist, so response time does not reveal whether it does. */
    private final String dummyHash;

    public AuthService(StaffRepository staffRepo, UnitRoleRepository roleRepo, PasswordEncoder encoder,
            AuditService audit, TestaProperties props) {
        this.staffRepo = staffRepo;
        this.roleRepo = roleRepo;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
        this.dummyHash = encoder.encode("testa-dummy-password");
    }

    public record UnitMembership(Long id, String name, UnitType type, List<Role> roles) {}

    public record Me(Long id, String email, String name, boolean systemAdmin, boolean mustChangePassword,
            List<UnitMembership> units) {}

    /** Verifies credentials. Every failure is the same 401, never revealing which part was wrong. */
    public Staff authenticate(String rawEmail, String password) {
        String email = Staff.normalizeEmail(rawEmail);
        boolean tooLong = password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
        Staff s = staffRepo.findByEmail(email).orElse(null);
        Instant now = Instant.now();

        if (s == null) {
            encoder.matches(tooLong ? "x" : password, dummyHash);
            audit.recordNow("LOGIN_FAILED", "ANONYMOUS", null, email, "unknown_account");
            throw unauthorized(BAD_CREDENTIALS);
        }
        if (s.isLocked(now)) {
            audit.recordNow("LOGIN_FAILED", "STAFF", s.getId(), email, "locked");
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "locked",
                    "密碼錯誤次數過多，帳號已暫時鎖定，請 " + props.login().lockMinutes() + " 分鐘後再試，或洽系統管理員。");
        }
        if (tooLong || !encoder.matches(password, s.getPasswordHash())) {
            int failures = s.getFailedLogins() + 1;
            s.setFailedLogins(failures);
            if (failures >= props.login().maxFailures()) {
                s.setLockedUntil(now.plus(Duration.ofMinutes(props.login().lockMinutes())));
                s.setFailedLogins(0);
            }
            staffRepo.save(s);
            audit.recordNow("LOGIN_FAILED", "STAFF", s.getId(), email, "bad_password");
            throw unauthorized(BAD_CREDENTIALS);
        }
        if (!s.isActive()) {
            audit.recordNow("LOGIN_FAILED", "STAFF", s.getId(), email, "inactive");
            throw unauthorized("這個帳號已停用，請洽系統管理員。");
        }
        s.setFailedLogins(0);
        s.setLockedUntil(null);
        s.setLastLoginAt(now);
        staffRepo.save(s);
        audit.recordNow("LOGIN", "STAFF", s.getId(), email, null);
        return s;
    }

    @Transactional(readOnly = true)
    public Me me(Long staffId) {
        Staff s = staffRepo.findById(staffId).orElseThrow(() -> unauthorized("請重新登入。"));
        Map<Long, UnitMembership> units = new LinkedHashMap<>();
        for (UnitRole r : roleRepo.findByStaffIdWithUnit(staffId)) {
            if (!r.getUnit().isActive()) continue;
            units.computeIfAbsent(r.getUnit().getId(),
                    id -> new UnitMembership(id, r.getUnit().getName(), r.getUnit().getType(), new ArrayList<>()))
                    .roles().add(r.getRole());
        }
        return new Me(s.getId(), s.getEmail(), s.getName(), s.isSystemAdmin(), s.isMustChangePassword(),
                List.copyOf(units.values()));
    }

    /** Changes one's own password. Returns the updated account; the caller swaps it into the session (other devices are signed out). */
    @Transactional
    public Staff changePassword(Long staffId, String current, String next) {
        Staff s = staffRepo.findById(staffId).orElseThrow(() -> unauthorized("請重新登入。"));
        if (current.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || !encoder.matches(current, s.getPasswordHash())) {
            throw ApiException.badRequest("wrong_password", "目前的密碼不正確。");
        }
        validateNewPassword(next);
        if (encoder.matches(next, s.getPasswordHash())) {
            throw ApiException.badRequest("same_password", "新密碼不能與目前的密碼相同。");
        }
        s.setPasswordHash(encoder.encode(next));
        s.setMustChangePassword(false);
        s.invalidateSessions();
        audit.record("PASSWORD_CHANGED", "staff", s.getId());
        return s;
    }

    public static void validateNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("weak_password", "密碼至少需要 " + MIN_PASSWORD + " 個字元。");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("long_password", "密碼太長了。");
        }
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }
}
