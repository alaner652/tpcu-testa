package tw.testa.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Stops requests before Spring Session touches the database while a restore is replacing it. */
public class MaintenanceFilter extends OncePerRequestFilter {
    private final BackupMaintenanceService maintenance;

    public MaintenanceFilter(BackupMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/maintenance")) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"active\":" + maintenance.active() + ",\"status\":\""
                    + maintenance.status() + "\"}");
            return;
        }
        if (maintenance.active() && request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Retry-After", "5");
            response.getWriter().write("{\"status\":503,\"title\":\"Service Unavailable\","
                    + "\"code\":\"maintenance\",\"detail\":\"系統正在還原備份，完成後會自動恢復。\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
