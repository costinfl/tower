package dev.tower.api.portability;

import dev.tower.portability.ConflictStrategy;
import dev.tower.portability.ExportService;
import dev.tower.portability.ImportReport;
import dev.tower.portability.ImportService;
import dev.tower.portability.TowerExport;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Export and import of Tower-owned information (ADR-010, issues #37 to #39).
 *
 * <p>ADR-009 leaves every developer with an isolated instance; this is the
 * interim way a team shares Release Packs, Promotion Paths and Handover
 * information until a shared deployment exists.
 */
@RestController
@RequestMapping("/api/portability")
public class PortabilityController {

    private final ExportService exportService;
    private final ImportService importService;
    private final String instanceName;
    private final ObjectMapper objectMapper;

    public PortabilityController(ExportService exportService, ImportService importService,
                                 @Value("${tower.instance-name}") String instanceName,
                                 ObjectMapper objectMapper) {
        this.exportService = exportService;
        this.importService = importService;
        this.instanceName = instanceName;
        this.objectMapper = objectMapper;
    }

    /**
     * @param includeObservations ADR-010 makes Observations an explicit choice,
     *                            since a colleague may want the plan without the sightings
     */
    @GetMapping("/export")
    public TowerExport export(
            @RequestParam(name = "includeObservations", defaultValue = "true") boolean includeObservations) {
        return exportService.export(instanceName, includeObservations);
    }

    @GetMapping("/export/download")
    public ResponseEntity<byte[]> download(
            @RequestParam(name = "includeObservations", defaultValue = "true") boolean includeObservations)
            throws Exception {
        TowerExport export = exportService.export(instanceName, includeObservations);
        byte[] body = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(export)
                .getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tower-export-" + instanceName + ".json\"")
                .body(body);
    }

    /**
     * Reports what an import would do, changing nothing.
     *
     * <p>ADR-010 forbids automatic merging, so the person running the import
     * sees the consequences before accepting them rather than afterwards.
     */
    @PostMapping("/import/preview")
    public ImportReport preview(@RequestBody TowerExport export,
                                @RequestParam(name = "strategy", defaultValue = "SKIP") ConflictStrategy strategy) {
        return importService.preview(export, strategy);
    }

    @PostMapping("/import")
    public ImportReport apply(@RequestBody TowerExport export,
                              @RequestParam(name = "strategy", defaultValue = "SKIP") ConflictStrategy strategy) {
        return importService.apply(export, strategy);
    }

    /** So the Viewer can say which instance it is exporting from. */
    @GetMapping("/instance")
    public InstanceView instance() {
        return new InstanceView(instanceName);
    }

    public record InstanceView(String name) {}
}
