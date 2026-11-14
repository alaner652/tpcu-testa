package tw.testa.exam;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByExamIdOrderBySeatAscIdAsc(Long examId);

    long countByExamId(Long examId);

    @Query("select c from Candidate c join fetch c.exam where c.ticket = ?1")
    List<Candidate> findByTicketWithExam(String ticket);

    @Query("select coalesce(max(c.seat), 0) from Candidate c where c.exam.id = ?1")
    int maxSeat(Long examId);

    @Query("select c.ticket from Candidate c where c.exam.id = ?1")
    List<String> ticketsOf(Long examId);

    /** [examId, status, count] per exam */
    @Query("select c.exam.id, c.status, count(c) from Candidate c where c.exam.id in ?1 group by c.exam.id, c.status")
    List<Object[]> countByStatus(Collection<Long> examIds);
}
