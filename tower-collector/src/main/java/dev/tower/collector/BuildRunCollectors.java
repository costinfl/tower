package dev.tower.collector;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.stereotype.Component;

import dev.tower.application.port.out.ApplicationVersionRepository;
import dev.tower.application.port.out.ConnectorCredentialsPort;
import dev.tower.application.port.out.ExternalBindingRepository;
import dev.tower.connector.api.CiCdConnector;

/**
 * Gives every installed CI/CD Connector a build Collector as well as a
 * deployment one (ADR-020).
 *
 * <p>Two Collectors over one Connector, registered separately, because they
 * answer different questions: {@link PipelineRunCollectors} appends Observations
 * from deployment runs, and this proposes candidate versions from build runs. A
 * single Collector doing both would have to be told which kind of job it was
 * looking at on every call, which is exactly the conflation ADR-020 exists to
 * prevent.
 *
 * <p>The same bean-factory arrangement {@link IssueTrackerCollectors} uses, for
 * the same reason: the connector modules are runtime-scope dependencies of
 * tower-api (ADR-003), so no configuration class can name their types.
 */
@Component
public class BuildRunCollectors implements BeanFactoryPostProcessor {

    /** Suffix on the generated bean name, so it reads as what it is. */
    static final String COLLECTOR_SUFFIX = "BuildRunCollector";

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
                    BeanDefinitionBuilder.genericBeanDefinition(BuildRunCollector.class,
                            () -> new BuildRunCollector(
                                    beanFactory.getBean(connectorBean, CiCdConnector.class),
                                    beanFactory.getBean(ExternalBindingRepository.class),
                                    beanFactory.getBean(ApplicationVersionRepository.class),
                                    beanFactory.getBean(ConnectorCredentialsPort.class)))
                            .getBeanDefinition());
        }
    }
}
