package tw.testa.unit;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UnitRoleRepository extends JpaRepository<UnitRole, Long> {

    boolean existsByUnitIdAndUnitActiveTrueAndStaffIdAndRoleIn(Long unitId, Long staffId, Collection<Role> roles);

    boolean existsByUnitIdAndStaffIdAndRole(Long unitId, Long staffId, Role role);

    long countByUnitIdAndRole(Long unitId, Role role);

    @Query("select r from UnitRole r join fetch r.unit where r.staff.id = ?1 order by r.unit.name, r.role")
    List<UnitRole> findByStaffIdWithUnit(Long staffId);

    @Query("select r from UnitRole r join fetch r.staff where r.unit.id = ?1 order by r.staff.name, r.role")
    List<UnitRole> findByUnitIdWithStaff(Long unitId);

    @Query("select r from UnitRole r join fetch r.unit join fetch r.staff order by r.unit.name")
    List<UnitRole> findAllWithUnitAndStaff();
}
