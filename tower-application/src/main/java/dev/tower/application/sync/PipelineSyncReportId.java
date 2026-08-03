package dev.tower.application.sync;

import java.util.UUID;

import dev.tower.application.service.InvalidRequestException;

/**
 * Stable identity of a {@link PipelineSyncReport}.
 *
 * <p>Its own type rather than a {@link SyncRunId}, for the reason the two
 * reports are separate at all: they record reading different kinds of system and
 * support different claims. Sharing an identity type would have invited sharing
 * a table, and ADR-020 records why that would put a statement on rows that
 * cannot support it.
 */
public record PipelineSyncReportId(UUID value) {

    public PipelineSyncReportId {
        if (value == null) {
            throw new InvalidRequestException("Pipeline sync report id is required.");
        }
    }

    public static PipelineSyncReportId newId() {
        return new PipelineSyncReportId(UUID.randomUUID());
    }

    public static PipelineSyncReportId of(String value) {
        try {
            return new PipelineSyncReportId(UUID.fromString(value));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException(
                    "Pipeline sync report id is not a valid identifier: " + value);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
