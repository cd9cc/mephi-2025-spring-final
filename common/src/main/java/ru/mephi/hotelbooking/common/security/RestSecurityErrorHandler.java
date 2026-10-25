package ru.mephi.hotelbooking.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import ru.mephi.hotelbooking.common.dto.ErrorResponse;
import ru.mephi.hotelbooking.common.trace.TraceContext;

@Slf4j
@RequiredArgsConstructor
public class RestSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        log.info("Unauthenticated request {} {}: {}",
                request.getMethod(), request.getRequestURI(), exception.getMessage());
        bearerEntryPoint.commence(request, response, exception);
        write(request, response, HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        log.info("Access denied {} {}", request.getMethod(), request.getRequestURI());
        bearerAccessDeniedHandler.handle(request, response, exception);
        write(request, response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(status, message, request.getRequestURI(),
                MDC.get(TraceContext.TRACE_ID_MDC_KEY));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
