package tw.testa.auth;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import tw.testa.common.ApiException;
import tw.testa.unit.Role;
import tw.testa.unit.UnitRoleRepository;

/**
 * The single entry point for permission checks: does this person hold this role in this unit.
 * Use {@code @PreAuthorize("@access.hasUnitRole(#unitId, 'OWNER')")} on controllers, or call require* in services.
 * System administrators manage accounts and units only; being an administrator never passes a unit role check (no banks, papers or scores).
 */
@Component("access")
public class AccessGuard {
    private final UnitRoleRepository roles;

    public AccessGuard(UnitRoleRepository roles) {
        this.roles = roles;
    }

    public StaffPrincipal currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof StaffPrincipal p ? p : null;
    }

    public StaffPrincipal current() {
        StaffPrincipal p = currentOrNull();
        if (p == null) throw ApiException.forbidden();
        return p;
    }

    public boolean isSystemAdmin() {
        StaffPrincipal p = currentOrNull();
        return p != null && p.systemAdmin();
    }

    /** Whether the signed-in staff member holds any of the given roles in the unit. */
    public boolean hasUnitRole(Long unitId, String... anyOf) {
        StaffPrincipal p = currentOrNull();
        if (p == null || unitId == null) return false;
        List<Role> wanted = Arrays.stream(anyOf).map(Role::valueOf).toList();
        return roles.existsByUnitIdAndUnitActiveTrueAndStaffIdAndRoleIn(unitId, p.id(), wanted);
    }

    public boolean hasUnitRole(Long unitId, Role... anyOf) {
        StaffPrincipal p = currentOrNull();
        if (p == null || unitId == null) return false;
        return roles.existsByUnitIdAndUnitActiveTrueAndStaffIdAndRoleIn(unitId, p.id(), List.of(anyOf));
    }

    /** A system administrator or the unit's owner: may manage unit members. */
    public boolean canManageUnit(Long unitId) {
        return isSystemAdmin() || hasUnitRole(unitId, Role.OWNER);
    }

    public void requireUnitRole(Long unitId, Role... anyOf) {
        if (!hasUnitRole(unitId, anyOf)) throw ApiException.forbidden();
    }

    public void requireSystemAdmin() {
        if (!isSystemAdmin()) throw ApiException.forbidden();
    }
}
