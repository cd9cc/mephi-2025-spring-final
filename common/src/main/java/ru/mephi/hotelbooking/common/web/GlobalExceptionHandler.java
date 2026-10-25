package ru.mephi.hotelbooking.common.web;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.mephi.hotelbooking.common.dto.ErrorResponse;
import ru.mephi.hotelbooking.common.dto.ErrorResponse.FieldViolation;
import ru.mephi.hotelbooking.common.exception.ConflictException;
import ru.mephi.hotelbooking.common.exception.NotFoundException;
import ru.mephi.hotelbooking.common.exception.ServiceUnavailableException;
import ru.mephi.hotelbooking.common.trace.TraceContext;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException e, WebRequest request) {
        return error(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException e, WebRequest request) {
        return error(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Object> handleServiceUnavailable(ServiceUnavailableException e, WebRequest request) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<Object> handleConcurrentModification(RuntimeException e, WebRequest request) {
        log.info("Concurrent modification: {}", e.getMessage());
        return error(HttpStatus.CONFLICT, "The resource was modified concurrently, retry the request", request);
    }

    @ExceptionHandler({PropertyReferenceException.class, InvalidDataAccessApiUsageException.class})
    public ResponseEntity<Object> handleInvalidQuery(RuntimeException e, WebRequest request) {
        log.warn("Invalid query parameters: {}", e.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Invalid sort or filter parameters", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException e, WebRequest request) {
        List<FieldViolation> details = e.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "Validation failed", request, details, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException e, WebRequest request) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException e, WebRequest request) {
        return error(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e, WebRequest request) {
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return error(status, "Validation failed", request, violations(e.getBindingResult()), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> details = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return error(status, "Validation failed", request, details, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = body instanceof ProblemDetail problem && problem.getDetail() != null
                ? problem.getDetail()
                : e.getMessage();
        return error(status, message, request, List.of(), headers);
    }

    private static ResponseEntity<Object> error(HttpStatusCode status, String message, WebRequest request) {
        return error(status, message, request, List.of(), HttpHeaders.EMPTY);
    }

    private static ResponseEntity<Object> error(HttpStatusCode status, String message, WebRequest request,
                                                List<FieldViolation> details, HttpHeaders headers) {
        String path = request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
        if (!status.is5xxServerError()) {
            log.info("Request {} rejected with {}: {}", path, status.value(), message);
        }
        ErrorResponse body = ErrorResponse.of(status, message, path, MDC.get(TraceContext.TRACE_ID_MDC_KEY), details);
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private static List<FieldViolation> violations(BindingResult result) {
        Stream<FieldViolation> fieldErrors = result.getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()));
        Stream<FieldViolation> objectErrors = result.getGlobalErrors().stream()
                .map(error -> new FieldViolation(error.getObjectName(), error.getDefaultMessage()));
        return Stream.concat(fieldErrors, objectErrors).toList();
    }
}
