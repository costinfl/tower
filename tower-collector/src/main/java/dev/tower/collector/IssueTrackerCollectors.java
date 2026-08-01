package dev.tower.collector;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.stereotype.Component;

import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.connector.api.IssueTrackerConnector;

/**
 * Gives every installed Issue Tracking Connector its own Collector (ADR-018).
 *
 * <p>Tower expects one tracker to be bound, but not one to be <em>installed</em>:
 * GitHub Issues and Jira can both be on the classpath while a team uses one of
 * them. A single {@code @Component} taking one {@code IssueTrackerConnector}
 * would start fine today and fail to start the day the second Connector shipped,
 * which is a poor moment to discover a wiring decision.
 *
 * <p>So the Collector is registered per Connector, and the choice of which one
 * to read is left where it belongs: with the binding the user configured, read
 * by {@code WorkItemService}.
 *
 * <p>This runs as a bean factory post-processor rather than a {@code @Bean}
 * method because the set of Connectors is only known once the classpath is
 * scanned — the connector modules are runtime-scope dependencies of tower-api
 * (ADR-003), so no configuration class can name their types. Each Connector's
 * bean is looked up lazily, inside the supplier, so nothing is instantiated
 * early.
 */
@Component
public class IssueTrackerCollectors implements BeanFactoryPostProcessor {

    /** Suffix on the generated bean name, so it reads as what it is. */
    static final String COLLECTOR_SUFFIX = "IssueTrackerCollector";

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            return;
        }

        // allowEagerInit is false: asking for the names must not build anything.
        for (String connectorBean : beanFactory.getBeanNamesForType(
                IssueTrackerConnector.class, true, false)) {
            registry.registerBeanDefinition(connectorBean + COLLECTOR_SUFFIX,
                    BeanDefinitionBuilder.genericBeanDefinition(IssueTrackerCollector.class,
                            () -> new IssueTrackerCollector(
                                    beanFactory.getBean(connectorBean, IssueTrackerConnector.class),
                                    beanFactory.getBean(ExternalBindingRepository.class),
                                    beanFactory.getBean(ConnectorCredentialsPort.class)))
                            .getBeanDefinition());
        }
    }
}
