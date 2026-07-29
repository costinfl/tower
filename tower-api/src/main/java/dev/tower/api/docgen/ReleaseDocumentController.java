package dev.tower.api.docgen;

import dev.tower.application.documentation.DocumentTemplate;
import dev.tower.application.documentation.DocumentTemplateId;
import dev.tower.application.port.in.DocumentTemplateUseCases;
import dev.tower.docgen.HtmlReleaseDocumentRenderer;
import dev.tower.docgen.MarkdownReleaseDocumentRenderer;
import dev.tower.docgen.ReleaseDocument;
import dev.tower.docgen.ReleaseDocumentAssembler;
import dev.tower.domain.releasepack.ReleasePackId;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Release documentation generated from the Canonical Model (FR-024, issue #28).
 *
 * <p>Generation is a read. Nothing is stored, and the same model always yields
 * the same bytes, so a document is disposable and reproducible (IA-03) rather
 * than an artifact that must be kept in step with reality.
 */
@RestController
@RequestMapping("/api/release-packs/{id}/documentation")
public class ReleaseDocumentController {

    private final ReleaseDocumentAssembler assembler;
    private final MarkdownReleaseDocumentRenderer renderer;
    private final HtmlReleaseDocumentRenderer htmlRenderer;
    private final DocumentTemplateUseCases templates;

    public ReleaseDocumentController(ReleaseDocumentAssembler assembler,
                                     MarkdownReleaseDocumentRenderer renderer,
                                     HtmlReleaseDocumentRenderer htmlRenderer,
                                     DocumentTemplateUseCases templates) {
        this.assembler = assembler;
        this.renderer = renderer;
        this.htmlRenderer = htmlRenderer;
        this.templates = templates;
    }

    /** The assembled document, for a client that wants to render it itself. */
    @GetMapping
    public ReleaseDocument get(@PathVariable String id) {
        return assembler.assemble(ReleasePackId.of(id));
    }

    /** The rendered Markdown, as text for preview. */
    @GetMapping(value = "/markdown", produces = MediaType.TEXT_PLAIN_VALUE)
    public String markdown(@PathVariable String id,
                           @RequestParam(name = "template", required = false) String template) {
        return renderer.render(assembler.assemble(ReleasePackId.of(id)), resolve(template));
    }

    /** The rendered Markdown as a download. */
    @GetMapping("/markdown/download")
    public ResponseEntity<byte[]> download(@PathVariable String id,
                                           @RequestParam(name = "template", required = false) String template) {
        ReleaseDocument document = assembler.assemble(ReleasePackId.of(id));
        DocumentTemplate chosen = resolve(template);
        byte[] body = renderer.render(document, chosen).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_MARKDOWN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(document.packName(), chosen, "md") + "\"")
                .body(body);
    }

    /**
     * The rendered HTML, for viewing in a browser (Milestone 3, OQ-009).
     *
     * <p>Served inline rather than as a download so it can be opened directly.
     * The page is self-contained — styles inlined, no script, no external
     * request — so it renders the same from a file share or a mail attachment
     * as it does from here.
     */
    @GetMapping(value = "/html", produces = MediaType.TEXT_HTML_VALUE)
    public String html(@PathVariable String id,
                       @RequestParam(name = "template", required = false) String template) {
        return htmlRenderer.render(assembler.assemble(ReleasePackId.of(id)), resolve(template));
    }

    /** The rendered HTML as a download. */
    @GetMapping("/html/download")
    public ResponseEntity<byte[]> downloadHtml(@PathVariable String id,
                                               @RequestParam(name = "template", required = false) String template) {
        ReleaseDocument document = assembler.assemble(ReleasePackId.of(id));
        DocumentTemplate chosen = resolve(template);
        byte[] body = htmlRenderer.render(document, chosen).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(document.packName(), chosen, "html") + "\"")
                .body(body);
    }

    /**
     * The template to render with (OQ-010, ADR-013).
     *
     * <p>Absent means the complete document, which is what Tower generated
     * before templates existed and still generates when nobody has chosen one.
     *
     * <p>An id that names no template is an error rather than a reason to fall
     * back. Falling back would hand someone a document containing the sections
     * they had deliberately removed, with nothing on the page to say so.
     */
    private DocumentTemplate resolve(String template) {
        if (template == null || template.isBlank()) {
            return DocumentTemplate.complete();
        }
        return templates.require(DocumentTemplateId.of(template.trim()));
    }

    /**
     * A filesystem-safe name derived from the pack name and the template.
     *
     * <p>No timestamp: two downloads of an unchanged release produce the same
     * file name and the same bytes, so they are trivially comparable.
     *
     * <p>The template name is part of it because two documents of the same
     * release rendered from different templates are different documents, and
     * two files called {@code release-2026-08.md} sitting in one downloads
     * folder is how the wrong one gets attached to a ticket.
     */
    private String fileName(String packName, DocumentTemplate template, String extension) {
        String slug = slug(packName);
        String base = slug.isBlank() ? "release-pack" : slug;
        if (!template.isBuiltIn()) {
            String suffix = slug(template.name());
            if (!suffix.isBlank()) {
                base = base + "-" + suffix;
            }
        }
        return base + "." + extension;
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}
