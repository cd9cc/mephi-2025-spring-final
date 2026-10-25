package ru.mephi.hotelbooking.common.trace;

import java.util.UUID;
import java.util.regex.Pattern;

public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String BOOKING_ID_MDC_KEY = "bookingId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private TraceContext() {
    }

    public static String resolveTraceId(String incoming) {
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
