package tw.testa.bank;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface BankRepository extends JpaRepository<Bank, Long> {

    @Query("select b from Bank b join fetch b.unit where b.unit.id in ?1 order by b.createdAt, b.id")
    List<Bank> findByUnitIds(Collection<Long> unitIds);

    /** 儲存時鎖住這一列，兩個人同時存只會有一個先過，另一個會看到版本不符。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Bank b where b.id = ?1")
    Optional<Bank> findForUpdate(Long id);
}
