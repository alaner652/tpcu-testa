package tw.testa.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import tw.testa.auth.StaffPrincipal;

/**
 * Writes the audit log within the caller's transaction, so failed actions leave no "success" entry.
 * Where the SecurityContext has no user yet (sign-in), use {@link #recordAs} (same transaction) or {@link #recordNow} (own transaction).
 */
@Service
public class AuditService {
    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(String action, String targetType, Object targetId, Long unitId, String reason, String detail) {
        save(action, targetType, targetId, unitId, reason, detail, null, null);
    }

    @Transactional
    public void record(String action, String targetType, Object targetId) {
        save(action, targetType, targetId, null, null, null, null, null);
    }

    /** With an explicit actor (no user in the SecurityContext during sign-in), in the caller's transaction. */
    @Transactional
    public void recordAs(String action, String actorType, Long actorId, String actorLabel, String detail) {
        save(action, null, null, null, null, detail, actorType, new Object[] { actorId, actorLabel });
    }

    /**
     * Own transaction, with an explicit actor.
     * Never call it inside another transaction: it takes a second pooled connection, and when many people arrive at once (a whole lab signing in)
     * every request holds one connection while waiting for another and the pool deadlocks. Inside a transaction use {@link #recordAs}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordNow(String action, String actorType, Long actorId, String actorLabel, String detail) {
        save(action, null, null, null, null, detail, actorType, new Object[] { actorId, actorLabel });
    }

    private void save(String action, String targetType, Object targetId, Long unitId, String reason, String detail,
            String actorTypeOverride, Object[] actorOverride) {
        String actorType = "SYSTEM";
        Long actorId = null;
        String actorLabel = null;
        if (actorTypeOverride != null) {
            actorType = actorTypeOverride;
            actorId = (Long) actorOverride[0];
            actorLabel = (String) actorOverride[1];
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof StaffPrincipal p) {
                actorType = "STAFF";
                actorId = p.id();
                actorLabel = p.email();
            } else if (auth != null && auth.getPrincipal() instanceof tw.testa.auth.CandidatePrincipal c) {
                actorType = "CANDIDATE";
                actorId = c.id();
                actorLabel = c.ticket();
            }
        }
        repo.save(new AuditLog(actorType, actorId, actorLabel, action, targetType,
                targetId == null ? null : String.valueOf(targetId), unitId, reason, detail, clientIp()));
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
