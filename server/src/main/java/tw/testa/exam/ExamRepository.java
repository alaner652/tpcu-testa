package tw.testa.exam;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExamRepository extends JpaRepository<Exam, Long> {
    @Query("select e from Exam e join fetch e.unit left join fetch e.bank where e.unit.id in ?1 order by e.createdAt desc, e.id desc")
    List<Exam> findByUnitIds(Collection<Long> unitIds);
}
