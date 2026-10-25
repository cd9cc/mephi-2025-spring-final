package ru.mephi.hotelbooking.common.dto;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        List<FieldViolation> details
) {

    public static ErrorResponse of(HttpStatusCode status, String message, String path, String traceId,
                                   List<FieldViolation> details) {
        HttpStatus known = HttpStatus.resolve(status.value());
        String error = known != null ? known.getReasonPhrase() : String.valueOf(status.value());
        return new ErrorResponse(Instant.now(), status.value(), error, message, path, traceId, details);
    }

    public static ErrorResponse of(HttpStatusCode status, String message, String path, String traceId) {
        return of(status, message, path, traceId, List.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
