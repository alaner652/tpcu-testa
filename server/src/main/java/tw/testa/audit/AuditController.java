package tw.testa.audit;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tw.testa.common.PageDto;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("@access.isSystemAdmin()")
public class AuditController {
    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    public record AuditDto(Long id, Instant at, String actorType, Long actorId, String actorLabel, String action,
            String targetType, String targetId, Long unitId, String reason, String detail, String ip) {}

    @GetMapping
    public PageDto<AuditDto> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String action) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200));
        var result = action == null || action.isBlank() ? repo.findAllByOrderByIdDesc(pr)
                : repo.findByActionOrderByIdDesc(action, pr);
        return PageDto.of(result, a -> new AuditDto(a.getId(), a.getAt(), a.getActorType(), a.getActorId(),
                a.getActorLabel(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getUnitId(), a.getReason(),
                a.getDetail(), a.getIp()));
    }
}
