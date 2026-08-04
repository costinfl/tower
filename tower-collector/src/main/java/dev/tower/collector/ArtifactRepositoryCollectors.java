package dev.tower.collector;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.stereotype.Component;

import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.connector.api.ArtifactRepositoryConnector;

/**
 * Gives every installed Artifact Repository Connector its own Collector
 * (ADR-021).
 *
 * <p>The same arrangement {@link IssueTrackerCollectors} uses, for the same
 * reason: the connector modules are runtime-scope dependencies of tower-api
 * (ADR-003), so no configuration class can name their types, and a single
 * {@code @Component} taking one Connector would start fine today and fail to
 * start the day a second one shipped.
 *
 * <p>Which Connector reads which artifact is left where it belongs — with the
 * coordinate binding the user configured, read by {@code ArtifactService}.
 */
@Component
public class ArtifactRepositoryCollectors implements BeanFactoryPostProcessor {

    /** Suffix on the generated bean name, so it reads as what it is. */
    static final String COLLECTOR_SUFFIX = "ArtifactRepositoryCollector";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }

        // allowEagerInit is false: asking for the names must not build anything.
        for (String connectorBean : beanFactory.getBeanNamesForType(
                ArtifactRepositoryConnector.class, true, false)) {
            registry.registerBeanDefinition(connectorBean + COLLECTOR_SUFFIX,
                    BeanDefinitionBuilder.genericBeanDefinition(ArtifactRepositoryCollector.class,
                            () -> new ArtifactRepositoryCollector(
                                    beanFactory.getBean(connectorBean, ArtifactRepositoryConnector.class),
                                    beanFactory.getBean(ConnectorCredentialsPort.class)))
                            .getBeanDefinition());
        }
    }
}
