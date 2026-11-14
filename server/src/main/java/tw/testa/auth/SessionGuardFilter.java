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
 * Checks on every request that the session identity is still valid, which is how the server can force sign-outs.
 * - Staff: signed out at once when disabled or when the password is reset (session_version changes).
 * - Candidates: one connection at a time; a new sign-in makes the old session_version stale.
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
