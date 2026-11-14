package tw.testa.attempt;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import tw.testa.attempt.AttemptDtos.LoginRequest;
import tw.testa.attempt.AttemptDtos.Paper;
import tw.testa.attempt.AttemptDtos.Saved;
import tw.testa.attempt.AttemptDtos.State;
import tw.testa.auth.CandidatePrincipal;
import tw.testa.exam.Candidate;

/** Candidate API. Apart from sign-in, everything uses the candidate identity in the session, so nobody can address another candidate's data. */
@RestController
@RequestMapping("/api/candidate")
public class CandidateController {
    private final AttemptService attempts;
    private final SecurityContextRepository contextRepo;

    public CandidateController(AttemptService attempts, SecurityContextRepository contextRepo) {
        this.attempts = attempts;
        this.contextRepo = contextRepo;
    }

    @PostMapping("/login")
    public State login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        Candidate c = attempts.login(body.ticket(), body.code());
        // Do not reuse the pre-login session (it may belong to someone else or to staff)
        HttpSession old = request.getSession(false);
        if (old != null) {
            java.util.Collections.list(old.getAttributeNames()).forEach(old::removeAttribute);
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(CandidatePrincipal.of(c), null,
                List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE"))));
        SecurityContextHolder.setContext(context);
        contextRepo.saveContext(context, request, response);
        return attempts.state(c.getId());
    }

    @GetMapping("/me")
    public State me(@AuthenticationPrincipal CandidatePrincipal me) {
        return attempts.state(me.id());
    }

    @PostMapping("/start")
    public State start(@AuthenticationPrincipal CandidatePrincipal me) {
        return attempts.start(me.id());
    }

    @GetMapping("/paper")
    public Paper paper(@AuthenticationPrincipal CandidatePrincipal me) {
        return attempts.paper(me.id());
    }

    @PutMapping("/answers/{questionId}")
    public Saved save(@AuthenticationPrincipal CandidatePrincipal me, @PathVariable String questionId,
            @RequestBody AnswerContent body) {
        return new Saved(attempts.saveAnswer(me.id(), questionId, body));
    }

    @PostMapping("/submit")
    public State submit(@AuthenticationPrincipal CandidatePrincipal me) {
        return attempts.submit(me.id());
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal CandidatePrincipal me) {
        attempts.left(me.id());
    }
}
