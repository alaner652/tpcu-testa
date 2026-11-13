package tw.testa.staff;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsBySystemAdminTrue();
    long countBySystemAdminTrueAndActiveTrue();
}
