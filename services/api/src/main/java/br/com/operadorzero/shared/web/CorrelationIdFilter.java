package br.com.operadorzero.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    private static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String candidate = request.getHeader(HEADER);
        String correlationId = candidate != null && candidate.matches("[A-Za-z0-9._-]{8,80}") ? candidate : UUID.randomUUID().toString();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlationId)) {
            response.setHeader(HEADER, correlationId);
            chain.doFilter(request, response);
        }
    }
}
