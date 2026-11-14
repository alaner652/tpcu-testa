package tw.testa.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import tw.testa.audit.AuditService;
import tw.testa.auth.SessionGuardFilter;
import tw.testa.auth.StaffPrincipal;
import tw.testa.staff.StaffRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityContextRepository contextRepo, StaffRepository staff,
            tw.testa.exam.CandidateRepository candidates,
            AuditService audit) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/setup", "/api/auth/csrf", "/api/health", "/api/candidate/login").permitAll()
                .requestMatchers("/api/auth/logout").authenticated()
                // Candidates may only reach /api/candidate, never staff APIs, and vice versa
                .requestMatchers("/api/candidate/**").hasRole("CANDIDATE")
                .requestMatchers("/api/**").hasRole("STAFF")
                .anyRequest().denyAll())
            // SPA: the CSRF token is in the XSRF-TOKEN cookie and writes send it back in the X-XSRF-TOKEN header
            .csrf(c -> c.spa())
            .securityContext(s -> s.securityContextRepository(contextRepo))
            .addFilterBefore(new SessionGuardFilter(staff, candidates), AuthorizationFilter.class)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(l -> l
                .logoutUrl("/api/auth/logout")
                .addLogoutHandler((request, response, authentication) -> {
                    if (authentication != null && authentication.getPrincipal() instanceof StaffPrincipal p) {
                        audit.recordNow("LOGOUT", "STAFF", p.id(), p.email(), null);
                    } else if (authentication != null
                            && authentication.getPrincipal() instanceof tw.testa.auth.CandidatePrincipal c) {
                        audit.recordNow("CANDIDATE_LOGOUT", "CANDIDATE", c.id(), c.ticket(), null);
                    }
                })
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, ex) ->
                    writeProblem(response, HttpStatus.UNAUTHORIZED, "unauthorized", "請先登入。"))
                .accessDeniedHandler((request, response, ex) ->
                    writeProblem(response, HttpStatus.FORBIDDEN, "forbidden", "你沒有這個操作的權限。")));
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Sign-in is handled by AuthService; this bean only stops Spring Boot from creating the default user. */
    @Bean
    UserDetailsService noUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value() + ",\"title\":\"" + status.getReasonPhrase()
                + "\",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }
}
