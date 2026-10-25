package ru.mephi.hotelbooking.gateway.trace;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import ru.mephi.hotelbooking.common.trace.TraceContext;

@Slf4j
@Component
public class TraceIdWebFilter implements WebFilter, Ordered {

    public static final String TRACE_ID_ATTRIBUTE = TraceIdWebFilter.class.getName() + ".traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = TraceContext.resolveTraceId(
                exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
        ServerWebExchange traced = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId)))
                .build();
        traced.getResponse().beforeCommit(() -> {
            traced.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
            return Mono.empty();
        });
        traced.getAttributes().put(TRACE_ID_ATTRIBUTE, traceId);

        long startedAt = System.nanoTime();
        return chain.filter(traced).doFinally(signal -> logRequest(traced, traceId, startedAt));
    }

    private static void logRequest(ServerWebExchange exchange, String traceId, long startedAt) {
        try (var ignored = MDC.putCloseable(TraceContext.TRACE_ID_MDC_KEY, traceId)) {
            log.info("{} {} -> {} ({} ms)",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getPath(),
                    exchange.getResponse().getStatusCode(),
                    (System.nanoTime() - startedAt) / 1_000_000);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
