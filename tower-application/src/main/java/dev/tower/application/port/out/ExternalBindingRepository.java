package dev.tower.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.tower.application.binding.ApplicationBinding;
import dev.tower.application.binding.EnvironmentBinding;
import dev.tower.application.binding.IssueTrackerBinding;
import dev.tower.application.binding.RepositoryBinding;
import dev.tower.domain.application.ApplicationId;
import dev.tower.domain.environment.EnvironmentId;

/**
 * Outbound port for External Bindings (ADR-012).
 *
 * <p>Bindings are user-owned configuration rather than business facts, so this
 * port offers ordinary create, replace and delete semantics — unlike
 * {@link ObservationRepository}, which offers no update because the thing it
 * stores is a historical fact.
 *
 * <p>A binding is keyed by the Tower concept and the Connector, so an
 * Environment can be observed by more than one Connector category while never
 * being ambiguous within one.
 */
public interface ExternalBindingRepository {

    EnvironmentBinding save(EnvironmentBinding binding);

    Optional<EnvironmentBinding> findEnvironmentBinding(EnvironmentId environmentId, String connectorId);

    List<EnvironmentBinding> findAllEnvironmentBindings(String connectorId);

    List<EnvironmentBinding> findAllEnvironmentBindings();

    void deleteEnvironmentBinding(EnvironmentId environmentId, String connectorId);

    ApplicationBinding save(ApplicationBinding binding);

    Optional<ApplicationBinding> findApplicationBinding(ApplicationId applicationId, String connectorId);

    List<ApplicationBinding> findAllApplicationBindings(String connectorId);

    List<ApplicationBinding> findAllApplicationBindings();

    void deleteApplicationBinding(ApplicationId applicationId, String connectorId);

    // Repository bindings (issue #3, ADR-014). A third kind rather than a field on
    // ApplicationBinding, because the two answer different questions: one says
    // which image running somewhere is this Application, the other says where its
    // code lives. A team may configure either without the other.

    RepositoryBinding save(RepositoryBinding binding);

    Optional<RepositoryBinding> findRepositoryBinding(ApplicationId applicationId, String connectorId);

    List<RepositoryBinding> findAllRepositoryBindings(String connectorId);

    List<RepositoryBinding> findAllRepositoryBindings();

    void deleteRepositoryBinding(ApplicationId applicationId, String connectorId);

    // Issue tracker bindings (ADR-018). Keyed by Connector alone, unlike every
    // other binding here: a work item belongs to a release rather than to one
    // Application, so there is one tracker named once rather than a Tower
    // concept on the left.
    IssueTrackerBinding save(IssueTrackerBinding binding);

    Optional<IssueTrackerBinding> findIssueTrackerBinding(String connectorId);

    List<IssueTrackerBinding> findAllIssueTrackerBindings();

    void deleteIssueTrackerBinding(String connectorId);
}
