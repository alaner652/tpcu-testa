package tw.testa.auth;

import java.io.Serializable;

import tw.testa.exam.Candidate;

/** Candidate identity stored in the session. Candidates have no account and belong to one exam. */
public record CandidatePrincipal(Long id, Long examId, String ticket, String name, int sessionVersion)
        implements Serializable, java.security.Principal {

    /** Short name for Authentication.getName(), stored in spring_session.principal_name; no name or email. */
    @Override
    public String getName() {
        return "candidate:" + id;
    }

    public static CandidatePrincipal of(Candidate c) {
        return new CandidatePrincipal(c.getId(), c.getExam().getId(), c.getTicket(), c.getName(), c.getSessionVersion());
    }
}
