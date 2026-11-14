package tw.testa.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Audit log entry. A database trigger blocks update and delete; there are no setters either. */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Instant at = Instant.now();

    @Column(name = "actor_type", nullable = false)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_label")
    private String actorLabel;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type")
    private String targetType;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "unit_id")
    private Long unitId;

    private String reason;

    private String detail;

    private String ip;

    protected AuditLog() {}

    AuditLog(String actorType, Long actorId, String actorLabel, String action, String targetType, String targetId,
            Long unitId, String reason, String detail, String ip) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.unitId = unitId;
        this.reason = reason;
        this.detail = detail;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public Instant getAt() { return at; }
    public String getActorType() { return actorType; }
    public Long getActorId() { return actorId; }
    public String getActorLabel() { return actorLabel; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public Long getUnitId() { return unitId; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public String getIp() { return ip; }
}
