package dev.tower.api.config;

import java.time.Clock;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.tower.application.port.out.EnvironmentRepository;
import dev.tower.application.port.out.ApplicationRepository;
import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ManualObservationCollector;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.application.port.out.PromotionPathRepository;
import dev.tower.application.port.out.ReleasePackRepository;
import dev.tower.application.service.ApplicationService;
import dev.tower.application.port.out.DeploymentObservationCollector;
import dev.tower.application.port.out.DocumentTemplateRepository;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.SyncRunRepository;
import dev.tower.application.service.DocumentTemplateService;
import dev.tower.application.service.EnvironmentService;
import dev.tower.application.service.ExternalBindingService;
import dev.tower.application.service.PromotionPathService;
import dev.tower.application.service.SynchronizationService;
import dev.tower.application.service.ObservationService;
import dev.tower.application.service.ReleasePackService;
import dev.tower.docgen.HtmlReleaseDocumentRenderer;
import dev.tower.docgen.MarkdownReleaseDocumentRenderer;
import dev.tower.docgen.ReleaseDocumentAssembler;
import dev.tower.portability.ExportService;
import dev.tower.portability.ImportService;

/**
 * Wires the framework-free application layer into Spring.
 *
 * <p>EnvironmentService and PromotionPathService (dev.tower.application.service) carry no
 * framework annotation on purpose, so tower-api - the composition root - is where they are
 * declared as beans and handed their repository adapters (tower-persistence, on the runtime
 * classpath only) and a {@link Clock}.
 */
@Configuration
public class ApplicationServicesConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EnvironmentService environmentService(
            EnvironmentRepository environmentRepository, PromotionPathRepository promotionPathRepository) {
        return new EnvironmentService(environmentRepository, promotionPathRepository);
    }

    @Bean
    public PromotionPathService promotionPathService(
            PromotionPathRepository promotionPathRepository, EnvironmentRepository environmentRepository,
            ReleasePackRepository releasePackRepository, Clock clock) {
        return new PromotionPathService(
                promotionPathRepository, environmentRepository, releasePackRepository, clock);
    }

    @Bean
    public ApplicationService applicationService(
            ApplicationRepository applicationRepository, ApplicationVersionRepository applicationVersionRepository,
            ReleasePackRepository releasePackRepository) {
        return new ApplicationService(
                applicationRepository, applicationVersionRepository, releasePackRepository);
    }

    @Bean
    public ExternalBindingService externalBindingService(
            ExternalBindingRepository externalBindingRepository, EnvironmentRepository environmentRepository,
            ApplicationRepository applicationRepository) {
        return new ExternalBindingService(
                externalBindingRepository, environmentRepository, applicationRepository);
    }

    /**
     * Takes every Collector on the classpath, so adding a Source Control Collector
     * later needs no change here. Spring injects an empty list when none is present,
     * which is the correct behaviour for a build without connector modules.
     */
    @Bean
    public SynchronizationService synchronizationService(
            List<DeploymentObservationCollector> collectors, SyncRunRepository syncRunRepository) {
        return new SynchronizationService(collectors, syncRunRepository);
    }

    @Bean
    public ReleasePackService releasePackService(
            ReleasePackRepository releasePackRepository, ApplicationVersionRepository applicationVersionRepository,
            PromotionPathRepository promotionPathRepository, Clock clock) {
        return new ReleasePackService(
                releasePackRepository, applicationVersionRepository, promotionPathRepository, clock);
    }

    @Bean
    public ObservationService observationService(
            ObservationRepository observationRepository, EnvironmentRepository environmentRepository,
            ApplicationVersionRepository applicationVersionRepository,
            ReleasePackRepository releasePackRepository,
            ManualObservationCollector manualObservationCollector, Clock clock) {
        return new ObservationService(observationRepository, environmentRepository,
                applicationVersionRepository, releasePackRepository, manualObservationCollector, clock);
    }

    @Bean
    public ReleaseDocumentAssembler releaseDocumentAssembler(
            ReleasePackService releasePackService, ApplicationService applicationService,
            PromotionPathService promotionPathService, EnvironmentService environmentService,
            ObservationService observationService) {
        return new ReleaseDocumentAssembler(releasePackService, applicationService,
                promotionPathService, environmentService, observationService);
    }

    @Bean
    public MarkdownReleaseDocumentRenderer markdownReleaseDocumentRenderer() {
        return new MarkdownReleaseDocumentRenderer();
    }

    @Bean
    public HtmlReleaseDocumentRenderer htmlReleaseDocumentRenderer() {
        return new HtmlReleaseDocumentRenderer();
    }

    @Bean
    public DocumentTemplateService documentTemplateService(DocumentTemplateRepository documentTemplateRepository) {
        return new DocumentTemplateService(documentTemplateRepository);
    }

    @Bean
    public ExportService exportService(
            EnvironmentRepository environmentRepository, ApplicationRepository applicationRepository,
            ApplicationVersionRepository applicationVersionRepository,
            PromotionPathRepository promotionPathRepository, ReleasePackRepository releasePackRepository,
            ObservationRepository observationRepository, Clock clock) {
        return new ExportService(environmentRepository, applicationRepository,
                applicationVersionRepository, promotionPathRepository, releasePackRepository,
                observationRepository, clock);
    }

    @Bean
    public ImportService importService(
            EnvironmentRepository environmentRepository, ApplicationRepository applicationRepository,
            ApplicationVersionRepository applicationVersionRepository,
            PromotionPathRepository promotionPathRepository, ReleasePackRepository releasePackRepository,
            ObservationRepository observationRepository) {
        return new ImportService(environmentRepository, applicationRepository,
                applicationVersionRepository, promotionPathRepository, releasePackRepository,
                observationRepository);
    }
}
