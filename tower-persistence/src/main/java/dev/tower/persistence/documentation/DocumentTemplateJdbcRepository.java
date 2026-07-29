package dev.tower.persistence.documentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tower.application.documentation.DocumentSection;
import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.port.out.DocumentTemplateRepository;
import dev.tower.application.service.ApplicationException;

/**
 * {@link DocumentTemplateRepository} adapter backed by the {@code document_template} and
 * {@code document_template_section} tables (V7__document_templates.sql).
 *
 * <p>Uses {@link JdbcClient} for the same reason the other adapters do: the types it stores are
 * immutable records with no framework dependency, so there is nothing for an ORM to map.
 *
 * <p>Save replaces the section rows outright rather than reconciling them. A template is a short
 * list that a user rewrote in full, so working out which rows moved would be effort spent to
 * arrive at the same state - and a reconciliation that got the positions wrong would change the
 * order of a document without anyone touching it.
 */
@Repository
public class DocumentTemplateJdbcRepository implements DocumentTemplateRepository {

    private final JdbcClient jdbcClient;

    public DocumentTemplateJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public DocumentTemplate save(DocumentTemplate template) {
        int updated = jdbcClient.sql("UPDATE document_template SET name = :name WHERE id = :id")
                .param("id", template.id().value())
                .param("name", template.name())
                .update();
        if (updated == 0) {
            jdbcClient.sql("INSERT INTO document_template (id, name) VALUES (:id, :name)")
                    .param("id", template.id().value())
                    .param("name", template.name())
                    .update();
        }

        jdbcClient.sql("DELETE FROM document_template_section WHERE document_template_id = :id")
                .param("id", template.id().value())
                .update();

        List<DocumentSection> sections = template.sections();
        for (int position = 0; position < sections.size(); position++) {
            jdbcClient.sql("""
                            INSERT INTO document_template_section (document_template_id, position, section)
                            VALUES (:id, :position, :section)
                            """)
                    .param("id", template.id().value())
                    .param("position", position)
                    .param("section", sections.get(position).name())
                    .update();
        }
        return template;
    }

    @Override
    public Optional<DocumentTemplate> findById(DocumentTemplateId id) {
        return jdbcClient.sql("SELECT id, name FROM document_template WHERE id = :id")
                .param("id", id.value())
                .query((rs, rowNum) -> new NameRow(
                        new DocumentTemplateId(rs.getObject("id", UUID.class)), rs.getString("name")))
                .optional()
                .map(this::withSections);
    }

    @Override
    public Optional<DocumentTemplate> findByName(String name) {
        // Case-insensitive, because the uniqueness rule it backs exists to keep a picker
        // unambiguous and "Handover only" beside "handover only" is not that.
        return jdbcClient.sql("SELECT id, name FROM document_template WHERE LOWER(name) = LOWER(:name)")
                .param("name", name == null ? "" : name.trim())
                .query((rs, rowNum) -> new NameRow(
                        new DocumentTemplateId(rs.getObject("id", UUID.class)), rs.getString("name")))
                .optional()
                .map(this::withSections);
    }

    @Override
    public List<DocumentTemplate> findAll() {
        List<NameRow> rows = jdbcClient.sql("SELECT id, name FROM document_template ORDER BY LOWER(name), name")
                .query((rs, rowNum) -> new NameRow(
                        new DocumentTemplateId(rs.getObject("id", UUID.class)), rs.getString("name")))
                .list();

        List<DocumentTemplate> templates = new ArrayList<>(rows.size());
        for (NameRow row : rows) {
            templates.add(withSections(row));
        }
        return templates;
    }

    @Override
    public void deleteById(DocumentTemplateId id) {
        // The section rows go with it through ON DELETE CASCADE (V7).
        jdbcClient.sql("DELETE FROM document_template WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    /**
     * Reads the sections for one template, in the order they were saved.
     *
     * <p>ORDER BY position is not a nicety. It is the whole reason the column exists: without it
     * the same template would render sections in whatever order the database returned, and the
     * byte-identical regeneration NFR-025 requires would fail intermittently rather than visibly.
     */
    private DocumentTemplate withSections(NameRow row) {
        List<DocumentSection> sections = jdbcClient.sql("""
                        SELECT section FROM document_template_section
                        WHERE document_template_id = :id ORDER BY position
                        """)
                .param("id", row.id().value())
                .query((rs, rowNum) -> {
                    String stored = rs.getString("section");
                    // A stored name Tower no longer knows means the database was written by a
                    // later version. Reported rather than skipped: quietly dropping it would
                    // hand back a template that renders less than the one that was saved.
                    return DocumentSection.parse(stored).orElseThrow(() -> new ApplicationException(
                            "Document Template \"" + row.name() + "\" refers to a document section this "
                                    + "version of Tower does not know: " + stored));
                })
                .list();

        return new DocumentTemplate(row.id(), row.name(), sections);
    }

    private record NameRow(DocumentTemplateId id, String name) {}
}
