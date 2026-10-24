package io.apiplatform.controlplane.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized declared bodies early. Limitation: chunked requests carry no Content-Length, so Tomcat's
 * own limits (server.tomcat.max-swallow-size, connection timeouts) remain the second line of defence.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeFilter extends OncePerRequestFilter {
    private final long maxBytes;

    public RequestSizeFilter(@Value("${app.max-request-bytes:2097152}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getContentLengthLong() > maxBytes) {
            res.setStatus(413);
            res.setContentType("application/problem+json");
            res.getWriter().write("{\"status\":413,\"code\":\"payload_too_large\",\"message\":\"Request body too large\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
