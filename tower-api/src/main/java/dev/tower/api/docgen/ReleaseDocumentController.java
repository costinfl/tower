package dev.tower.api.docgen;

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

    public ReleaseDocumentController(ReleaseDocumentAssembler assembler,
                                     MarkdownReleaseDocumentRenderer renderer) {
        this.assembler = assembler;
        this.renderer = renderer;
    }

    /** The assembled document, for a client that wants to render it itself. */
    @GetMapping
    public ReleaseDocument get(@PathVariable String id) {
        return assembler.assemble(ReleasePackId.of(id));
    }

    /** The rendered Markdown, as text for preview. */
    @GetMapping(value = "/markdown", produces = MediaType.TEXT_PLAIN_VALUE)
    public String markdown(@PathVariable String id) {
        return renderer.render(assembler.assemble(ReleasePackId.of(id)));
    }

    /** The rendered Markdown as a download. */
    @GetMapping("/markdown/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        ReleaseDocument document = assembler.assemble(ReleasePackId.of(id));
        byte[] body = renderer.render(document).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_MARKDOWN)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName(document.packName()) + "\"")
                .body(body);
    }

    /**
     * A filesystem-safe name derived from the pack name.
     *
     * <p>No timestamp: two downloads of an unchanged release produce the same
     * file name and the same bytes, so they are trivially comparable.
     */
    private String fileName(String packName) {
        String slug = packName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return (slug.isBlank() ? "release-pack" : slug) + ".md";
    }
}
