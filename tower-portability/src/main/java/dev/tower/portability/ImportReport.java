package dev.tower.portability;

import java.util.ArrayList;
import java.util.List;

/**
 * What an import did, or would do (issue #38).
 *
 * <p>Import is previewable: the same analysis runs with or without applying, so
 * a developer can see the consequences before accepting them. Nothing in
 * ADR-010 is served by an import that surprises the person running it.
 */
public record ImportReport(
        boolean applied,
        String sourceInstance,
        int schemaVersion,
        List<Entry> entries) {

    public ImportReport {
        entries = List.copyOf(entries);
    }

    public enum Outcome { CREATED, REPLACED, SKIPPED, DUPLICATED, UNCHANGED }

    public record Entry(String type, String id, String name, Outcome outcome, String detail) {}

    public long count(Outcome outcome) {
        return entries.stream().filter(e -> e.outcome() == outcome).count();
    }

    public List<Entry> conflicts() {
        return entries.stream()
                .filter(e -> e.outcome() != Outcome.CREATED && e.outcome() != Outcome.UNCHANGED)
                .toList();
    }

    static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        void add(String type, String id, String name, Outcome outcome, String detail) {
            entries.add(new Entry(type, id, name, outcome, detail));
        }

        ImportReport build(boolean applied, String sourceInstance, int schemaVersion) {
            return new ImportReport(applied, sourceInstance, schemaVersion, entries);
        }
    }
}
