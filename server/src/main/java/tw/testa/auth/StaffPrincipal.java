package tw.testa.auth;

import java.io.Serializable;

import tw.testa.staff.Staff;

/** 存在 Session 裡的教職員身分。單位角色不放這裡，每次檢查都查資料庫，撤銷才會立即生效。 */
public record StaffPrincipal(Long id, String email, String name, boolean systemAdmin, int sessionVersion)
        implements Serializable {

    public static StaffPrincipal of(Staff s) {
        return new StaffPrincipal(s.getId(), s.getEmail(), s.getName(), s.isSystemAdmin(), s.getSessionVersion());
    }
}
