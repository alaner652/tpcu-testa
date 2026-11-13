package tw.testa.auth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import tw.testa.exam.CandidateRepository;
import tw.testa.staff.StaffRepository;

/**
 * 每個請求確認 Session 裡的身分仍然有效，這是「伺服器可以強制登出」的實作。
 * - 教職員：帳號被停用、密碼被重設（session_version 改變）就立刻登出。
 * - 考生：同時只允許一個連線，新登入會讓舊連線的 session_version 對不上。
 */
public class SessionGuardFilter extends OncePerRequestFilter {
    private final StaffRepository staff;
    private final CandidateRepository candidates;

    public SessionGuardFilter(StaffRepository staff, CandidateRepository candidates) {
        this.staff = staff;
        this.candidates = candidates;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        boolean valid = true;
        if (principal instanceof StaffPrincipal p) {
            valid = staff.findById(p.id())
                    .map(s -> s.isActive() && s.getSessionVersion() == p.sessionVersion()
                            && s.isSystemAdmin() == p.systemAdmin())
                    .orElse(false);
        } else if (principal instanceof CandidatePrincipal p) {
            valid = candidates.findById(p.id()).map(c -> c.getSessionVersion() == p.sessionVersion()).orElse(false);
        }
        if (!valid) {
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
