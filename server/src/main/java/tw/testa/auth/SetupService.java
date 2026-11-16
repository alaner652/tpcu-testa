package tw.testa.auth;

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
import tw.testa.staff.Staff;
import tw.testa.staff.StaffRepository;

/**
 * First-run setup: while there is no system administrator, the first person to open the site creates one.
 * There is no token: whoever deploys the server must complete setup before anyone else can reach it.
 * Setup closes for good once an administrator exists.
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

    public SetupService(StaffRepository staffRepo, PasswordEncoder encoder, AuditService audit, JdbcTemplate jdbc) {
        this.staffRepo = staffRepo;
        this.encoder = encoder;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        if (required()) log.warn("First-run setup is open: whoever opens the site first creates the system administrator. Do it now.");
    }

    public boolean required() {
        return !staffRepo.existsBySystemAdminTrue();
    }

    @Transactional
    public Staff createFirstAdmin(String email, String name, String password) {
        if (!required()) throw closed();
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
}
