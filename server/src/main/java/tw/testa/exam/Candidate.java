package tw.testa.exam;

import java.time.Instant;

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

/** One candidate on the roster. Belongs to one exam and has no account. */
@Entity
@Table(name = "candidate")
public class Candidate {
    public enum Status { IDLE, IN, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id")
    private Exam exam;

    @Column(nullable = false, updatable = false)
    private String ticket;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String dept;

    @Column(nullable = false)
    private int seat;

    @Column(name = "login_code", nullable = false)
    private String loginCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IDLE;

    @Column(name = "failed_logins", nullable = false)
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    @Column(name = "leave_count", nullable = false)
    private int leaveCount;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Candidate() {}

    public Candidate(Exam exam, String ticket, String name, String dept, int seat, String loginCode) {
        this.exam = exam;
        this.ticket = ticket;
        this.name = name;
        this.dept = dept;
        this.seat = seat;
        this.loginCode = loginCode;
    }

    public int getSessionVersion() { return sessionVersion; }
    public void newSession() { sessionVersion++; lastLoginAt = Instant.now(); }
    public int getLeaveCount() { return leaveCount; }
    public void left() { leaveCount++; }
    public Long getId() { return id; }
    public Exam getExam() { return exam; }
    public String getTicket() { return ticket; }
    public String getName() { return name; }
    public String getDept() { return dept; }
    public int getSeat() { return seat; }
    public String getLoginCode() { return loginCode; }
    public void setLoginCode(String loginCode) { this.loginCode = loginCode; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getFailedLogins() { return failedLogins; }
    public void setFailedLogins(int failedLogins) { this.failedLogins = failedLogins; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
}
