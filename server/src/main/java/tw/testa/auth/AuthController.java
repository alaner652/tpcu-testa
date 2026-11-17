package tw.testa.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tw.testa.settings.SettingsService;
import tw.testa.staff.Staff;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccessGuard access;
    private final SecurityContextRepository contextRepo;
    private final SetupService setup;
    private final SettingsService settings;

    public AuthController(AuthService auth, AccessGuard access, SecurityContextRepository contextRepo,
            SetupService setup, SettingsService settings) {
        this.auth = auth;
        this.settings = settings;
        this.setup = setup;
        this.access = access;
        this.contextRepo = contextRepo;
    }

    public record LoginRequest(@NotBlank(message = "請輸入帳號。") String email,
            @NotBlank(message = "請輸入密碼。") String password) {}

    public record PasswordRequest(@NotBlank(message = "請輸入目前的密碼。") String currentPassword,
            @NotBlank(message = "請輸入新密碼。") String newPassword) {}

    public record SetupRequest(
            @NotBlank(message = "請輸入 Email。") @Email(message = "Email 格式不正確。") @Size(max = 254) String email,
            @NotBlank(message = "請輸入姓名。") @Size(max = 100) String name,
            @NotBlank(message = "請輸入密碼。") String password) {}

    public record SetupStatus(boolean required) {}

    @GetMapping("/setup")
    public SetupStatus setupStatus() {
        return new SetupStatus(setup.required());
    }

    /** First-run setup: creates the first system administrator and signs them in. */
    @PostMapping("/setup")
    public AuthService.Me createFirstAdmin(@Valid @RequestBody SetupRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Staff staff = setup.createFirstAdmin(body.email(), body.name(), body.password());
        if (request.getSession(false) != null) request.changeSessionId();
        signIn(staff, request, response);
        return auth.me(staff.getId());
    }

    /** Called once at frontend start-up to get the XSRF-TOKEN cookie; writes send it back in the X-XSRF-TOKEN header. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public AuthService.Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Staff staff = auth.authenticate(body.email(), body.password());
        // New session id after sign-in, against session fixation
        if (request.getSession(false) != null) request.changeSessionId();
        signIn(staff, request, response);
        return auth.me(staff.getId());
    }

    @GetMapping("/me")
    public AuthService.Me me() {
        return auth.me(access.current().id());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Staff staff = auth.changePassword(access.current().id(), body.currentPassword(), body.newPassword());
        signIn(staff, request, response);
    }

    private void signIn(Staff staff, HttpServletRequest request, HttpServletResponse response) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_STAFF"));
        if (staff.isSystemAdmin()) authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(StaffPrincipal.of(staff), null, authorities));
        SecurityContextHolder.setContext(context);
        contextRepo.saveContext(context, request, response);
        // Staff only: candidates keep the server's session timeout, which outlasts any exam
        request.getSession().setMaxInactiveInterval(settings.staffIdleMinutes() * 60);
    }
}
