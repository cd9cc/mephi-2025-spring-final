package ru.mephi.hotelbooking.common.trace;

import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class TracePropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = MDC.get(TraceContext.TRACE_ID_MDC_KEY);
        if (traceId != null) {
            request.getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
