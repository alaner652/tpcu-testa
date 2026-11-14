package tw.testa.auth;

import java.io.Serializable;

import tw.testa.exam.Candidate;

/** 存在 Session 裡的考生身分。考生沒有帳號，只屬於一場考試。 */
public record CandidatePrincipal(Long id, Long examId, String ticket, String name, int sessionVersion)
        implements Serializable, java.security.Principal {

    /** Authentication.getName() 用的短名稱，會寫進 spring_session.principal_name；不放姓名與 Email。 */
    @Override
    public String getName() {
        return "candidate:" + id;
    }

    public static CandidatePrincipal of(Candidate c) {
        return new CandidatePrincipal(c.getId(), c.getExam().getId(), c.getTicket(), c.getName(), c.getSessionVersion());
    }
}
