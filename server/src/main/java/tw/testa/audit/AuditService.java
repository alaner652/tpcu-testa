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
 * 寫入操作紀錄。跟著呼叫端的交易走：動作沒成功就不留「成功」紀錄。
 * 登入失敗這類要獨立留存的，用 {@link #recordNow}。
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

    /** 獨立交易，並可指定行為人（登入當下 SecurityContext 還沒有人）。 */
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
