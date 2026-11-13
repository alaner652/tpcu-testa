package tw.testa.attempt;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "answer")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id")
    private Attempt attempt;

    @Column(name = "question_id", nullable = false, updatable = false)
    private String questionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private AnswerContent content;

    @Column(name = "auto_score")
    private BigDecimal autoScore;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Answer() {}

    public Answer(Attempt attempt, String questionId, AnswerContent content) {
        this.attempt = attempt;
        this.questionId = questionId;
        this.content = content;
    }

    public Long getId() { return id; }
    public Attempt getAttempt() { return attempt; }
    public String getQuestionId() { return questionId; }
    public AnswerContent getContent() { return content; }
    public void setContent(AnswerContent content) { this.content = content; this.updatedAt = Instant.now(); }
    public BigDecimal getAutoScore() { return autoScore; }
    public void setAutoScore(BigDecimal autoScore) { this.autoScore = autoScore; }
}
