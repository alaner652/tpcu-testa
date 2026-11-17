package tw.testa.staff;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsBySystemAdminTrue();
    long countBySystemAdminTrueAndActiveTrue();
    List<Staff> findByEmailIn(Collection<String> emails);

    /** Active accounts whose name or email contains the (lower-case) text. */
    @Query("select s from Staff s where s.active = true and (locate(?1, lower(s.email)) > 0 or locate(?1, lower(s.name)) > 0) order by s.name, s.id")
    List<Staff> searchActive(String text, Pageable page);
}
