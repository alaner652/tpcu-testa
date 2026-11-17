package tw.testa.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import tw.testa.common.PageDto;
import tw.testa.staff.Staff;
import tw.testa.unit.Unit;

@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("@access.isSystemAdmin()")
public class AuditController {
    private final AuditLogRepository repo;

    public AuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    public record AuditDto(Long id, Instant at, String actorType, Long actorId, String actorLabel, String action,
            String targetType, String targetId, Long unitId, String reason, String detail, String ip) {

        public static AuditDto of(AuditLog a) {
            return new AuditDto(a.getId(), a.getAt(), a.getActorType(), a.getActorId(), a.getActorLabel(), a.getAction(),
                    a.getTargetType(), a.getTargetId(), a.getUnitId(), a.getReason(), a.getDetail(), a.getIp());
        }
    }

    /**
     * Newest first. q searches the actor (email or candidate number), the target, the detail and the reason;
     * from is inclusive and to exclusive. size goes up to 500 so the page can export a filtered list.
     */
    @GetMapping
    public PageDto<AuditDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String action, @RequestParam(required = false) String q,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 500), Sort.by(Sort.Direction.DESC, "id"));
        return PageDto.of(repo.findAll(filter(action, q, from, to), pr), AuditDto::of);
    }

    static Specification<AuditLog> filter(String action, String q, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (action != null && !action.isBlank()) where.add(cb.equal(root.get("action"), action));
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                // Entries keep ids: also match staff by name or email (as actor or target) and units by name
                Subquery<Long> people = query.subquery(Long.class);
                Root<Staff> s = people.from(Staff.class);
                people.select(s.get("id")).where(cb.or(cb.like(cb.lower(s.get("name")), pattern, '\\'),
                        cb.like(cb.lower(s.get("email")), pattern, '\\')));
                Subquery<String> targets = query.subquery(String.class);
                Root<Staff> t = targets.from(Staff.class);
                // target_id is text (it also holds backup names): compare as text
                targets.select(((JpaExpression<Long>) t.<Long>get("id")).cast(String.class)).where(cb.or(cb.like(cb.lower(t.get("name")), pattern, '\\'),
                        cb.like(cb.lower(t.get("email")), pattern, '\\')));
                Subquery<Long> units = query.subquery(Long.class);
                Root<Unit> u = units.from(Unit.class);
                units.select(u.get("id")).where(cb.like(cb.lower(u.get("name")), pattern, '\\'));
                where.add(cb.or(
                        cb.like(cb.lower(root.get("actorLabel")), pattern, '\\'),
                        cb.like(cb.lower(root.get("targetId")), pattern, '\\'),
                        cb.like(cb.lower(root.get("detail")), pattern, '\\'),
                        cb.like(cb.lower(root.get("reason")), pattern, '\\'),
                        cb.and(cb.equal(root.get("actorType"), "STAFF"), root.get("actorId").in(people)),
                        cb.and(cb.equal(root.get("targetType"), "staff"), root.get("targetId").in(targets)),
                        root.get("unitId").in(units)));
            }
            if (from != null) where.add(cb.greaterThanOrEqualTo(root.get("at"), from));
            if (to != null) where.add(cb.lessThan(root.get("at"), to));
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
