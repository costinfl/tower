package dev.tower.api.error;

import java.time.Instant;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import dev.tower.application.service.ApplicationException;
import dev.tower.application.service.InvalidRequestException;
import dev.tower.application.service.NotFoundException;
import dev.tower.domain.shared.DomainConflictException;
import dev.tower.domain.shared.DomainException;

/**
 * Issue #4: a single, consistent JSON error contract for every API error - timestamp, status,
 * message and path. Exception detail (stack traces, exception class names) is logged
 * server-side but never placed in the response body, so error responses never leak
 * implementation detail to a caller.
 *
 * <p>Issue #12 adds the three exceptions the application and domain layers raise:
 * {@link NotFoundException} (404), {@link ApplicationException} (409, a cross-aggregate rule such
 * as a name collision or a broken reference), {@link DomainConflictException} (409, a single-aggregate
 * rule the current state forbids) and {@link DomainException} (400, a single-aggregate
 * invariant such as a blank name).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ErrorResponse> handleApplicationException(
            ApplicationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Input that is wrong on its own terms rather than in conflict with existing state — an
     * unparseable version pattern, say. 400 rather than the 409 an {@link ApplicationException}
     * answers, because resubmitting it unchanged will fail identically.
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(
            InvalidRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /**
     * A well-formed request the aggregate's current state forbids, such as adding a second version
     * of an Application a Release Pack already contains. Declared before the DomainException
     * handler because DomainConflictException extends it, and Spring picks the most specific match.
     */
    @ExceptionHandler(DomainConflictException.class)
    public ResponseEntity<ErrorResponse> handleDomainConflict(
            DomainConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Resource not found.", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed." : message, request);
    }

    /**
     * A query parameter Spring could not convert to the type the handler declares —
     * {@code ?at=yesterday} on a point-in-time state request, say (Milestone 4).
     *
     * <p>400 rather than the 500 the catch-all would otherwise give: nothing failed
     * on the server, the request was malformed, and a caller told "an unexpected
     * error occurred" has no way to know they can fix it themselves.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = Instant.class.equals(ex.getRequiredType())
                // Named concretely, because the ISO-8601 requirement is not
                // guessable from a rejection alone.
                ? "'" + ex.getName() + "' must be an instant in ISO-8601 form,"
                        + " for example 2026-08-04T09:00:00Z."
                : "'" + ex.getName() + "' is not in a form Tower can read.";
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * A body Jackson could not read at all — malformed JSON, a string where a
     * number belongs.
     *
     * <p>400 rather than 500: nothing failed on the server, the request was not
     * well formed. The parser's own message is not returned, because it names
     * types and offsets from Tower's internals rather than anything the caller
     * can act on.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.BAD_REQUEST,
                "The request body could not be read. It must be well-formed JSON.", request);
    }

    /**
     * The right resource, the wrong verb.
     *
     * <p>405 with an Allow header, which is what tells a caller — or a reader
     * poking at the API by hand — that a resource is read-only. Returning 500
     * instead made every such resource look broken rather than deliberate,
     * which matters most for the ones that are read-only on purpose (ADR-001).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String allowed = ex.getSupportedHttpMethods() == null ? ""
                : ex.getSupportedHttpMethods().stream().map(Object::toString)
                        .collect(Collectors.joining(", "));
        ErrorResponse body = new ErrorResponse(
                Instant.now(), HttpStatus.METHOD_NOT_ALLOWED.value(),
                allowed.isBlank()
                        ? "That method is not supported on this resource."
                        : "That method is not supported on this resource. Allowed: " + allowed + ".",
                request.getRequestURI());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (!allowed.isBlank()) {
            response.header("Allow", allowed);
        }
        return response.body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Full detail (including the stack trace) goes to the server log only.
        log.error("Unhandled exception while processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
