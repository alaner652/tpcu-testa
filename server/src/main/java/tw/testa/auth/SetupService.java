package tw.testa.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.audit.AuditService;
import tw.testa.common.ApiException;
import tw.testa.config.TestaProperties;
import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;

/**
 * First-run setup: while there is no system administrator, whoever holds the setup token can create the first one.
 * The token comes from TESTA_SETUP_TOKEN or is generated at start-up and written to the server log, so only someone
 * with access to the server can use it. Setup closes for good once an administrator exists.
 */
@Service
public class SetupService {
    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    /** Serialises concurrent setup requests across transactions (and across instances sharing the database). */
    private static final long LOCK_KEY = 0x7465737461L;

    private final StaffRepository staffRepo;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final boolean tokenFromEnvironment;
    private final String token;

    public SetupService(TestaProperties props, StaffRepository staffRepo, PasswordEncoder encoder, AuditService audit,
            JdbcTemplate jdbc) {
        this.staffRepo = staffRepo;
        this.encoder = encoder;
        this.audit = audit;
        this.jdbc = jdbc;
        this.tokenFromEnvironment = !props.setup().token().isBlank();
        this.token = tokenFromEnvironment ? props.setup().token() : randomToken();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        if (!required()) return;
        if (tokenFromEnvironment) {
            log.warn("First-run setup is open: create the system administrator on the sign-in page with TESTA_SETUP_TOKEN");
        } else {
            log.warn("First-run setup is open: create the system administrator on the sign-in page with setup token {}", token);
        }
    }

    public boolean required() {
        return !staffRepo.existsBySystemAdminTrue();
    }

    @Transactional
    public Staff createFirstAdmin(String suppliedToken, String email, String name, String password) {
        if (!required()) throw closed();
        if (!MessageDigest.isEqual(bytes(token), bytes(suppliedToken))) {
            log.warn("First-run setup rejected: wrong setup token");
            throw new ApiException(HttpStatus.FORBIDDEN, "bad_setup_token", "設定碼不正確，請向伺服器管理者確認。");
        }
        jdbc.execute("select pg_advisory_xact_lock(" + LOCK_KEY + ")");
        if (!required()) throw closed();
        AuthService.validateNewPassword(password);
        if (staffRepo.existsByEmail(Staff.normalizeEmail(email))) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken", "這個 Email 已經有帳號了。");
        }
        Staff s = staffRepo.save(new Staff(email, encoder.encode(password), name.trim(), true));
        audit.recordAs("SYSTEM_SETUP", "STAFF", s.getId(), s.getEmail(), null);
        log.info("First-run setup done, system administrator id {}", s.getId());
        return s;
    }

    private static ApiException closed() {
        return new ApiException(HttpStatus.CONFLICT, "setup_done", "系統已經完成初始設定，請直接登入。");
    }

    private static byte[] bytes(String s) {
        return (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
    }

    private static String randomToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
