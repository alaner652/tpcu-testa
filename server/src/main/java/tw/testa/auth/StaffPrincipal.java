package tw.testa.auth;

import java.io.Serializable;

import tw.testa.staff.Staff;

/** Staff identity stored in the session. Unit roles are not kept here; every check reads the database so revocation is immediate. */
public record StaffPrincipal(Long id, String email, String name, boolean systemAdmin, int sessionVersion)
        implements Serializable, java.security.Principal {

    /** Short name for Authentication.getName(), stored in spring_session.principal_name; no name or email. */
    @Override
    public String getName() {
        return "staff:" + id;
    }

    public static StaffPrincipal of(Staff s) {
        return new StaffPrincipal(s.getId(), s.getEmail(), s.getName(), s.isSystemAdmin(), s.getSessionVersion());
    }
}
