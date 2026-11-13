package tw.testa.attempt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByAttemptId(Long attemptId);
    Optional<Answer> findByAttemptIdAndQuestionId(Long attemptId, String questionId);
}
