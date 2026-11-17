package tw.testa.unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.testa.audit.AuditService;
import tw.testa.staff.Staff;

/** Grants unit roles; shared by unit member management and account creation. */
@Service
public class MembershipService {
    private final UnitRoleRepository roleRepo;
    private final AuditService audit;

    public MembershipService(UnitRoleRepository roleRepo, AuditService audit) {
        this.roleRepo = roleRepo;
        this.audit = audit;
    }

    /** Grants the roles the staff member does not hold yet and returns the new assignments. */
    @Transactional
    public List<UnitRole> grant(Unit unit, Staff staff, Collection<Role> roles) {
        List<UnitRole> added = new ArrayList<>();
        for (Role role : roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles)) {
            if (roleRepo.existsByUnitIdAndStaffIdAndRole(unit.getId(), staff.getId(), role)) continue;
            added.add(roleRepo.save(new UnitRole(unit, staff, role)));
            audit.record("UNIT_ROLE_GRANTED", "staff", staff.getId(), unit.getId(), null, role.name());
        }
        return added;
    }
}
