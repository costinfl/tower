package dev.tower.api.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the architectural boundaries documented in
 * docs/planning/Implementation-Plan.md, "Enforced Boundaries".
 *
 * <p>These rules exist so that a boundary violation fails the build rather than
 * depending on a reviewer noticing it. Each rule cites the requirement or
 * decision record it enforces.
 *
 * <p>This test lives in tower-api because tower-api is the only module with the
 * whole system on its classpath. Adapters reach it at runtime scope, which is
 * still part of the test classpath.
 */
@AnalyzeClasses(packages = "dev.tower", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundariesTest {

    private static final String DOMAIN = "dev.tower.domain..";
    private static final String APPLICATION = "dev.tower.application..";
    private static final String CONNECTORS = "dev.tower.connector..";
    private static final String PERSISTENCE = "dev.tower.persistence..";
    private static final String CONFIG = "dev.tower.config..";
    private static final String DOCGEN = "dev.tower.docgen..";
    private static final String PORTABILITY = "dev.tower.portability..";
    private static final String API = "dev.tower.api..";

    /**
     * NFR-004, NFR-005, NFR-006: the business domain is independent of vendor
     * products and frameworks. This is the rule that makes the technology
     * selection reversible, so it is the strictest one here.
     */
    @ArchTest
    static final ArchRule domain_depends_only_on_the_jdk =
            classes().that().resideInAPackage(DOMAIN)
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(DOMAIN, "java..", "javax.annotation..")
                    .because("the domain must carry no framework or vendor dependency (NFR-004, NFR-006)");

    /**
     * CM-03, ADR-003: vendor-specific concepts terminate at the Connector
     * boundary. The domain and the application layer must never see them.
     */
    @ArchTest
    static final ArchRule domain_and_application_do_not_depend_on_connectors =
            noClasses().that().resideInAnyPackage(DOMAIN, APPLICATION)
                    .should().dependOnClassesThat().resideInAnyPackage(CONNECTORS)
                    .because("vendor concepts terminate at the Connector boundary (CM-03, ADR-003)");

    /**
     * IA: the application layer defines ports; adapters implement them.
     * Dependencies point inward, never outward.
     */
    @ArchTest
    static final ArchRule application_does_not_depend_on_adapters =
            noClasses().that().resideInAPackage(APPLICATION)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(PERSISTENCE, CONFIG, DOCGEN, PORTABILITY, API)
                    .because("the application layer owns the ports and must not know their adapters (IA)");

    /**
     * CRC-Cards.md, Viewer responsibilities: the Viewer never communicates
     * directly with an External System. tower-api is the composition root, so
     * the adapters are on its runtime classpath, but no API class may reference
     * a Connector type. Runtime scope makes the compiler enforce this too.
     */
    @ArchTest
    static final ArchRule api_does_not_reference_connectors =
            noClasses().that().resideInAPackage(API)
                    .should().dependOnClassesThat().resideInAnyPackage(CONNECTORS)
                    .because("the Viewer and the API layer never reach External Systems directly (CRC-Cards.md)");

    /**
     * IA, FR-038: everything the Viewer and documentation generation read comes
     * from the Canonical Model, reached through the application layer.
     */
    @ArchTest
    static final ArchRule docgen_and_portability_read_through_the_application_layer =
            noClasses().that().resideInAnyPackage(DOCGEN, PORTABILITY)
                    .should().dependOnClassesThat().resideInAnyPackage(CONNECTORS, PERSISTENCE)
                    .because("generated output originates from the Canonical Model, not from adapters (IA, FR-038)");

    /**
     * ADR-009: the deferred move to PostgreSQL stays contained in one module.
     * An H2 type anywhere else would silently widen that migration.
     */
    @ArchTest
    static final ArchRule h2_is_confined_to_the_persistence_module =
            noClasses().that().resideOutsideOfPackage(PERSISTENCE)
                    .should().dependOnClassesThat().resideInAnyPackage("org.h2..")
                    .because("the embedded database must not leak outside tower-persistence (ADR-009)");

    /**
     * NFR-028, ADR-009: secret handling lives in exactly one auditable place.
     */
    @ArchTest
    static final ArchRule secret_handling_is_confined_to_the_config_module =
            noClasses().that().resideOutsideOfPackage(CONFIG)
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.ulisesbocchio..", "org.jasypt..")
                    .because("only tower-config reads or writes credential material (NFR-028)");

    /**
     * ADR-010: an export file is meant to be shared between developers, so it
     * must never carry credentials or configuration.
     */
    @ArchTest
    static final ArchRule portability_does_not_touch_configuration =
            noClasses().that().resideInAPackage(PORTABILITY)
                    .should().dependOnClassesThat().resideInAnyPackage(CONFIG)
                    .because("export files must never contain credentials or configuration (ADR-010)");

    /**
     * CM-05, FR-037, ADR-012: a vendor client terminates at its own Connector
     * module, exactly as H2 terminates at tower-persistence. Adding a second
     * Deployment Platform must not require a change anywhere else, and it cannot
     * if no other module can name a Kubernetes type.
     *
     * <p>Honest about what enforces what. The primary guard is Maven scope, not
     * this rule: tower-connector-k8s is a runtime-scope dependency of tower-api,
     * so fabric8 is absent from every compile classpath but its own module and a
     * violation in tower-api fails to compile before ArchUnit runs. Verified by
     * writing one — the compiler rejected it, and this rule never got the chance.
     *
     * <p>The rule earns its place as a backstop for the case scope cannot cover:
     * someone adding fabric8 as a compile dependency to another module. It is
     * kept for that, not because it is doing the work today.
     */
    @ArchTest
    static final ArchRule kubernetes_types_are_confined_to_their_connector_module =
            noClasses().that().resideOutsideOfPackage("dev.tower.connector.k8s..")
                    .should().dependOnClassesThat().resideInAnyPackage("io.fabric8..")
                    .because("a vendor client terminates at its Connector (CM-05, FR-037, ADR-012)");

    /**
     * The same containment for JGit, the Source Control Connector's client
     * (ADR-014).
     *
     * <p>Scope is the primary guard here too — tower-connector-git is a
     * runtime-scope dependency of tower-api — and this is the backstop for
     * someone adding JGit as a compile dependency elsewhere.
     *
     * <p>Worth noting what this rule protects that the Kubernetes one does not.
     * JGit can clone, commit, tag and push; the Connector uses reference
     * discovery alone. Keeping its types inside one small module is what makes
     * that restraint reviewable in one file rather than something to trust across
     * the codebase.
     */
    @ArchTest
    static final ArchRule git_types_are_confined_to_their_connector_module =
            noClasses().that().resideOutsideOfPackage("dev.tower.connector.git..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.eclipse.jgit..")
                    .because("a vendor client terminates at its Connector (CM-05, FR-037, ADR-014)");

    /**
     * ADR-001, CM-01, FR-036: Connectors observe and never modify. This is a
     * naming heuristic rather than a proof — it does not catch a write issued
     * through a method named for something else, such as scale. The Kubernetes
     * Connector's own tests assert the HTTP verbs actually put on the wire,
     * which is stronger, and the cluster's audit log is stronger still.
     */
    @ArchTest
    static final ArchRule connectors_expose_no_write_operation =
            noClasses().that().resideInAPackage(CONNECTORS)
                    .should().callMethodWhere(
                            com.tngtech.archunit.core.domain.JavaCall.Predicates.target(
                                    com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching(
                                            "(?i)^(create|update|delete|insert|put|post|patch|write|save|deploy|trigger|execute)$")))
                    .because("Connectors are read-only with respect to External Systems (ADR-001, CM-01, FR-036)");

    /**
     * ADR-001, FR-035, FR-036: Tower never executes deployments. A Connector
     * that names a method for mutation is a design error even before it has a
     * body.
     */
    @ArchTest
    static final ArchRule connector_methods_are_not_named_for_mutation =
            methods().that().areDeclaredInClassesThat().resideInAPackage(CONNECTORS)
                    .and().arePublic()
                    .should().haveNameNotMatching(
                            "(?i)^(create|update|delete|remove|write|save|deploy|trigger|execute|apply|modify).*")
                    .because("Connectors observe and never interfere (ADR-001, P2)");
}
