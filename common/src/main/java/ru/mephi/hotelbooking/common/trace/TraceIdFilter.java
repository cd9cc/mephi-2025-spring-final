package ru.mephi.hotelbooking.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceContext.resolveTraceId(request.getHeader(TraceContext.TRACE_ID_HEADER));
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        long startedAt = System.nanoTime();
        try (var ignored = MDC.putCloseable(TraceContext.TRACE_ID_MDC_KEY, traceId)) {
            chain.doFilter(request, response);
            if (request.getRequestURI().startsWith("/api/")) {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - startedAt) / 1_000_000);
            }
        }
    }
}
