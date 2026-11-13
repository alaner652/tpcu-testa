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
 * 權限檢查的唯一入口：「這個人在這個單位有沒有這個角色」。
 * 在 Controller 上用 {@code @PreAuthorize("@access.hasUnitRole(#unitId, 'OWNER')")}，或在 Service 裡呼叫 require*。
 * 系統管理員只管帳號與單位，不會因為是管理員就通過單位角色檢查（碰不到題庫、答卷、分數）。
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

    /** 目前登入的教職員在該單位是否擁有任一指定角色。 */
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

    /** 系統管理員，或該單位的負責人：可以管理單位成員。 */
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
