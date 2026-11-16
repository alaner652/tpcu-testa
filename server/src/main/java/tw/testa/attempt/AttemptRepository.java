package tw.testa.attempt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByCandidateId(Long candidateId);

    /** Locks the attempt so saves, submission and auto-submit for one candidate never run concurrently. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.candidate.id = ?1")
    Optional<Attempt> findForUpdateByCandidateId(Long candidateId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = ?1")
    Optional<Attempt> findForUpdate(Long id);

    @Query("select a.id from Attempt a where a.submittedAt is null and a.deadlineAt < ?1")
    List<Long> findOverdueIds(Instant before);

    /** A candidate is still answering: their attempt is open and its deadline has not passed. */
    @Query("select count(a) > 0 from Attempt a where a.submittedAt is null and a.deadlineAt > ?1")
    boolean existsInProgress(Instant now);

    @Query("select a from Attempt a where a.exam.id = ?1")
    List<Attempt> findByExamId(Long examId);

    @Query("select a.id from Attempt a where a.submittedAt is null and a.exam.id = ?1")
    List<Long> findOpenIdsByExam(Long examId);
}
