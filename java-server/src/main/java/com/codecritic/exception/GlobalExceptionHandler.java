package com.codecritic.exception;

import com.codecritic.agent.PythonAgentClient.PythonAgentRequestException;
import com.codecritic.agent.PythonAgentClient.PythonAgentUnavailableException;
import com.codecritic.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Central translation of application and framework exceptions into consistent
 * {@link ApiErrorResponse} bodies.
 *
 * <p>Handlers are ordered from the most specific exception type to the catch-all
 * {@link Exception} handler so that callers receive actionable status codes and internal
 * details never leak into responses.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Creates the exception advice instance.
     */
    public GlobalExceptionHandler() {
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles failed authentication attempts.
     *
     * @param ex the rejected-credentials exception
     * @return HTTP 401 with an {@code UNAUTHORIZED} error body
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiErrorResponse.of("UNAUTHORIZED", ex.getMessage(), null)
        );
    }

    /**
     * Handles request bodies that cannot be parsed.
     *
     * @param ex the unreadable-message exception
     * @return HTTP 400 with a {@code BAD_REQUEST} error body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("BAD_REQUEST", "Malformed request body", null)
        );
    }

    /**
     * Handles bean-validation failures on controller arguments, reporting the first field
     * error encountered.
     *
     * @param ex the validation exception carrying the binding result
     * @return HTTP 400 with a {@code VALIDATION_ERROR} error body
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("VALIDATION_ERROR", message, null)
        );
    }

    /**
     * Preserve explicit HTTP statuses raised by services instead of letting the
     * catch-all handler turn them into 500s. Registration conflicts, for example,
     * must stay 409 after losing the unique-index race.
     *
     * @param ex the exception carrying the intended HTTP status and reason
     * @return a response with the requested status; 5xx reasons are replaced with a generic
     *         message and logged in full
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String statusName = status instanceof HttpStatus httpStatus
                ? httpStatus.name()
                : "HTTP_" + status.value();
        String message = ex.getReason();
        if (status.is5xxServerError()) {
            log.error("Request failed with status {}", status.value(), ex);
            message = "An unexpected error occurred";
        } else {
            log.info("Request rejected with status {}: {}", status.value(), message);
        }
        if (message == null || message.isBlank()) {
            message = statusName;
        }
        return ResponseEntity.status(status).body(
                ApiErrorResponse.of(statusName, message, null)
        );
    }

    /**
     * Handles requests rejected because of an invalid argument.
     *
     * @param ex the illegal-argument exception
     * @return HTTP 400 with a {@code BAD_REQUEST} error body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("BAD_REQUEST", ex.getMessage(), null)
        );
    }

    /**
     * Handles missing resources raised by application code.
     *
     * @param ex the not-found exception
     * @return HTTP 404 with a {@code NOT_FOUND} error body
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.info("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of("NOT_FOUND", ex.getMessage(), null)
        );
    }

    /**
     * Unmapped static-resource paths arrive as {@link NoResourceFoundException} and were
     * being swallowed by the catch-all {@code Exception} handler below, so any request to
     * a path with no handler -- {@code /ready}, a typo'd endpoint, a stale bookmark --
     * returned <em>500 Internal Server Error</em> and logged a full stack trace. Callers
     * reasonably escalated monitoring on it, and a probe expecting 404 got 500 instead.
     *
     * @param ex the exception identifying the unmatched resource path and HTTP method
     * @return HTTP 404 with a {@code NOT_FOUND} error body naming the requested endpoint
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        // Spring reports the resource path without its leading slash ("ready", not
        // "/ready"), so normalise it before echoing it back to the caller.
        String path = ex.getResourcePath();
        if (path != null && !path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        log.info("No handler for {} {}", ex.getHttpMethod(), path);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ApiErrorResponse.of("NOT_FOUND",
                        "No endpoint " + ex.getHttpMethod() + " " + path, null)
        );
    }

    /**
     * The AI-backed endpoints are proxies for the Python agent. When that service is
     * down, a generic 500 makes it look as if /review, /generate-tests,
     * /analyze-repository, or /debug do not exist. A 503 with an actionable message
     * keeps the Java boundary honest without exposing connection details.
     *
     * @param ex the exception indicating the Python agent cannot be reached
     * @return HTTP 503 with an {@code AGENT_UNAVAILABLE} error body
     */
    @ExceptionHandler(PythonAgentUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAgentUnavailable(PythonAgentUnavailableException ex) {
        log.warn("Advisory analysis service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiErrorResponse.of("AGENT_UNAVAILABLE",
                        "The advisory analysis service is unavailable. Start the Python agent and retry.", null)
        );
    }

    /**
     * Handles requests the Python agent rejected as invalid.
     *
     * @param ex the exception indicating the agent refused the submitted input
     * @return HTTP 400 with an {@code AGENT_REQUEST_REJECTED} error body
     */
    @ExceptionHandler(PythonAgentRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleAgentRequestRejected(PythonAgentRequestException ex) {
        log.info("Advisory analysis service rejected the request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.of("AGENT_REQUEST_REJECTED",
                        "The advisory analysis service rejected the request. Check the submitted input.", null)
        );
    }

    /**
     * Catch-all handler for unexpected failures, logging the cause while returning a
     * sanitised body.
     *
     * @param ex the unhandled exception
     * @return HTTP 500 with an {@code INTERNAL_ERROR} error body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Internal server error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", null)
        );
    }
}