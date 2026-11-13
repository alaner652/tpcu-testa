package tw.testa.bank;

import java.math.BigDecimal;

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
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id")
    private Bank bank;

    @Column(name = "client_id", nullable = false, updatable = false)
    private String clientId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String stem;

    @Column(nullable = false)
    private BigDecimal points;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private QuestionContent content;

    protected Question() {}

    public Question(Bank bank, String clientId) {
        this.bank = bank;
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public Bank getBank() { return bank; }
    public String getClientId() { return clientId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public BigDecimal getPoints() { return points; }
    public void setPoints(BigDecimal points) { this.points = points; }
    public QuestionContent getContent() { return content; }
    public void setContent(QuestionContent content) { this.content = content; }
}
