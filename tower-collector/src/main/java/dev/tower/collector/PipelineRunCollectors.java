package dev.tower.collector;

import java.time.Clock;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.stereotype.Component;

import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.application.port.out.ObservationRepository;
import dev.tower.connector.api.CiCdConnector;

/**
 * Gives every installed CI/CD Connector its own Collector (ADR-020).
 *
 * <p>The same arrangement {@link IssueTrackerCollectors} makes, for the same
 * reason: a single {@code @Component} taking one {@code CiCdConnector} would
 * start fine while there is one on the classpath and fail to start the day a
 * second arrived, which is a poor moment to discover a wiring decision.
 *
 * <p>Here it is likelier than it was there. ADR-020 was written for a Jenkins
 * with a fair chance of being decommissioned inside a year, which means a period
 * with two CI Connectors installed at once — the old one still holding history
 * and the new one taking over — is not a hypothetical but the expected shape of
 * the migration this Connector exists to survive.
 *
 * <p>Runs as a bean factory post-processor rather than a {@code @Bean} method
 * because the set of Connectors is only known once the classpath is scanned: the
 * connector modules are runtime-scope dependencies of tower-api (ADR-003), so no
 * configuration class can name their types.
 */
@Component
public class PipelineRunCollectors implements BeanFactoryPostProcessor {

    /** Suffix on the generated bean name, so it reads as what it is. */
    static final String COLLECTOR_SUFFIX = "PipelineRunCollector";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }

        // allowEagerInit is false: asking for the names must not build anything.
        for (String connectorBean : beanFactory.getBeanNamesForType(
                CiCdConnector.class, true, false)) {
            registry.registerBeanDefinition(connectorBean + COLLECTOR_SUFFIX,
                    BeanDefinitionBuilder.genericBeanDefinition(PipelineRunCollector.class,
                            () -> new PipelineRunCollector(
                                    beanFactory.getBean(connectorBean, CiCdConnector.class),
                                    beanFactory.getBean(ExternalBindingRepository.class),
                                    beanFactory.getBean(ObservationRepository.class),
                                    beanFactory.getBean(ApplicationVersionRepository.class),
                                    beanFactory.getBean(ConnectorCredentialsPort.class),
                                    beanFactory.getBean(Clock.class)))
                            .getBeanDefinition());
        }
    }
}
