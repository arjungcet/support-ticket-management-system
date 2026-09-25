package com.supportdesk.shared.error;

import com.supportdesk.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The only producer of error responses: every failure becomes RFC 9457 Problem Details with the project extensions
 * of spec/api-contract.md §2 ({@code code}, {@code correlationId}, {@code timestamp}, {@code errors}). Internals
 * (stack traces, SQL, class names) never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(RequestValidationException.class)
    ResponseEntity<Map<String, Object>> validation(RequestValidationException ex, HttpServletRequest request) {
        log.info("Request rejected: {} field error(s)", ex.errors().size());
        return problem(ex.errorCode(), ex.detail(), request, ex.errors(), Map.of(), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, Object>> domain(DomainException ex, HttpServletRequest request) {
        log.info("Request rejected: {}", ex.errorCode());
        return problem(ex.errorCode(), ex.detail(), request, List.of(), ex.extensions(), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> optimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent modification detected at commit time");
        return problem(ErrorCode.TICKET_CONCURRENT_MODIFICATION, "The ticket was modified by someone else.", request,
                List.of(), Map.of(), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problem(ErrorCode.MALFORMED_REQUEST, "The request body could not be read.", request, List.of(),
                Map.of(), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return problem(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Request bodies must be sent as application/json.", request,
                List.of(), Map.of(), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        String allowed = headers.getAllow().stream().map(HttpMethod::name).collect(Collectors.joining(", "));
        return problem(ErrorCode.METHOD_NOT_ALLOWED, "This method is not supported here. Allowed: " + allowed + ".",
                request, List.of(), Map.of(), headers);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException ex, HttpServletRequest request) {
        return problem(ErrorCode.RESOURCE_NOT_FOUND, "No resource exists at this path.", request, List.of(), Map.of(),
                HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error (correlationId={})", CorrelationIdFilter.current(request), ex);
        return problem(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Please try again or contact support with the correlation id.",
                request, List.of(), Map.of(), HttpHeaders.EMPTY);
    }

    private ResponseEntity<Map<String, Object>> problem(ErrorCode code, String detail, HttpServletRequest request,
            List<FieldError> errors, Map<String, Object> extensions, HttpHeaders headers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", code.type());
        body.put("title", code.title());
        body.put("status", code.httpStatus());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("code", code.name());
        body.put("correlationId", CorrelationIdFilter.current(request));
        body.put("timestamp", clock.instant().toString());
        body.put("errors", errors);
        body.putAll(extensions);
        return ResponseEntity.status(code.httpStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
