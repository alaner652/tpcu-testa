package tw.testa.bank;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByBankIdOrderByPositionAscIdAsc(Long bankId);

    @Query("select q from Question q where q.bank.id in ?1 order by q.bank.id, q.position, q.id")
    List<Question> findByBankIds(Collection<Long> bankIds);
}
