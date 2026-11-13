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
import jakarta.validation.constraints.NotBlank;
import tw.testa.staff.Staff;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccessGuard access;
    private final SecurityContextRepository contextRepo;

    public AuthController(AuthService auth, AccessGuard access, SecurityContextRepository contextRepo) {
        this.auth = auth;
        this.access = access;
        this.contextRepo = contextRepo;
    }

    public record LoginRequest(@NotBlank(message = "請輸入帳號。") String email,
            @NotBlank(message = "請輸入密碼。") String password) {}

    public record PasswordRequest(@NotBlank(message = "請輸入目前的密碼。") String currentPassword,
            @NotBlank(message = "請輸入新密碼。") String newPassword) {}

    /** 前端啟動時先呼叫一次，拿到 XSRF-TOKEN cookie；之後的寫入請求把它放進 X-XSRF-TOKEN 標頭。 */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/login")
    public AuthService.Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        Staff staff = auth.authenticate(body.email(), body.password());
        // 登入後換一個 Session ID，避免 session fixation
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
    }
}
