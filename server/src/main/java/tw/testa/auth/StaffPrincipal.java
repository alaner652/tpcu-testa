package tw.testa.auth;

import java.io.Serializable;

import tw.testa.staff.Staff;

/** 存在 Session 裡的教職員身分。單位角色不放這裡，每次檢查都查資料庫，撤銷才會立即生效。 */
public record StaffPrincipal(Long id, String email, String name, boolean systemAdmin, int sessionVersion)
        implements Serializable, java.security.Principal {

    /** Authentication.getName() 用的短名稱，會寫進 spring_session.principal_name；不放姓名與 Email。 */
    @Override
    public String getName() {
        return "staff:" + id;
    }

    public static StaffPrincipal of(Staff s) {
        return new StaffPrincipal(s.getId(), s.getEmail(), s.getName(), s.isSystemAdmin(), s.getSessionVersion());
    }
}
