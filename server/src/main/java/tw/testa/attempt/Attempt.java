package tw.testa.attempt;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import tw.testa.exam.Candidate;
import tw.testa.exam.Exam;

/** One candidate's attempt at one exam. */
@Entity
@Table(name = "attempt")
public class Attempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id")
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "auto_submitted", nullable = false)
    private boolean autoSubmitted;

    @Column(name = "anon_id", nullable = false, updatable = false)
    private String anonId;

    @Column(name = "auto_score")
    private BigDecimal autoScore;

    @Column(name = "final_score")
    private BigDecimal finalScore;

    @Column(name = "extra_minutes", nullable = false)
    private int extraMinutes;

    protected Attempt() {}

    public Attempt(Candidate candidate, Exam exam, Instant startedAt, Instant deadlineAt, String anonId) {
        this.candidate = candidate;
        this.exam = exam;
        this.startedAt = startedAt;
        this.deadlineAt = deadlineAt;
        this.anonId = anonId;
    }

    public boolean isSubmitted() { return submittedAt != null; }

    public Long getId() { return id; }
    public Candidate getCandidate() { return candidate; }
    public Exam getExam() { return exam; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public int getExtraMinutes() { return extraMinutes; }
    /** Extra time: moves the deadline and accumulates the minutes added. */
    public void extend(int minutes) {
        this.deadlineAt = deadlineAt.plus(java.time.Duration.ofMinutes(minutes));
        this.extraMinutes += minutes;
    }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public boolean isAutoSubmitted() { return autoSubmitted; }
    public void setAutoSubmitted(boolean autoSubmitted) { this.autoSubmitted = autoSubmitted; }
    public String getAnonId() { return anonId; }
    public BigDecimal getAutoScore() { return autoScore; }
    public void setAutoScore(BigDecimal autoScore) { this.autoScore = autoScore; }
    public BigDecimal getFinalScore() { return finalScore; }
    public void setFinalScore(BigDecimal finalScore) { this.finalScore = finalScore; }
}
