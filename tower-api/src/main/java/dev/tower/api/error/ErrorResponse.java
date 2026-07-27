package dev.tower.api.error;

import java.time.Instant;

/**
 * Issue #4: consistent JSON error contract for every API error response. Never carries a
 * stack trace or raw exception detail - see ApiExceptionHandler.
 */
public record ErrorResponse(Instant timestamp, int status, String message, String path) {
}
