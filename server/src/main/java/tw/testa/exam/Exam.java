package tw.testa.exam;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tw.testa.bank.Bank;
import tw.testa.staff.Staff;
import tw.testa.unit.Unit;

@Entity
@Table(name = "exam")
public class Exam {
    public enum Mode { ONLINE, CLASSROOM }

    public enum Status { DRAFT, PUBLISHED, ENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private Staff createdBy;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(nullable = false)
    private int duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    private ExamSnapshot snapshot;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "results_published_at")
    private Instant resultsPublishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Exam() {}

    public Exam(Unit unit, Bank bank, Staff createdBy, String name, int duration, Mode mode) {
        this.unit = unit;
        this.bank = bank;
        this.createdBy = createdBy;
        this.name = name;
        this.duration = duration;
        this.mode = mode;
    }

    /** 給前端的狀態：草稿、已排程（已發布未到開始時間）、進行中、已結束。 */
    public String phase(Instant now) {
        return switch (status) {
            case DRAFT -> "draft";
            case ENDED -> "ended";
            case PUBLISHED -> startAt != null && now.isBefore(startAt) ? "scheduled" : "live";
        };
    }

    public Instant getResultsPublishedAt() { return resultsPublishedAt; }
    public void setResultsPublishedAt(Instant at) { this.resultsPublishedAt = at; }
    public Long getId() { return id; }
    public Unit getUnit() { return unit; }
    public Bank getBank() { return bank; }
    public void setBank(Bank bank) { this.bank = bank; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getStartAt() { return startAt; }
    public void setStartAt(Instant startAt) { this.startAt = startAt; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public ExamSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(ExamSnapshot snapshot) { this.snapshot = snapshot; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
