# ADR-012 — External Bindings Map Tower Concepts To Vendor Locators

**Status**

Accepted

---

## Context

A Connector reads an External System and returns what it found there.

What it finds is expressed in the vocabulary of that system.

A Kubernetes cluster returns namespaces, workloads and container images.

Tower's Canonical Model contains Environments, Applications and Application Versions.

Nothing in either vocabulary says which namespace is the Environment named Production, or which image is the Application named Customer API.

That correspondence is knowledge held by the team, not a fact discoverable from either side.

ADR-003 requires business concepts to reference Connector categories rather than specific products.

CM-03 requires vendor-specific concepts to terminate at the Connector boundary.

CM-05 requires the Canonical Model never to depend on vendor APIs.

Adding a namespace field to Environment, or an image field to Application, would violate all three.

It would also be wrong on its own terms.

ADR-005 establishes that an Environment is shared across Promotion Paths, and Domain-Model.md gives it only identity, name and Stage.

An Environment is a business concept that happens to be realised somewhere; it is not a Kubernetes namespace.

The correspondence must therefore be recorded somewhere that is neither the Domain Model nor the Connector.

A second question arrives with the first.

A Kubernetes workload reports a container image such as a registry path and a tag.

Deriving an Application Version from that tag is a decision about the team's own conventions, and teams differ.

Some tag with the version alone, some prefix a release name, some append a build number or a commit hash.

Tower cannot guess, and guessing wrongly would produce Observations that look authoritative and are false.

---

## Decision

Tower shall record External Bindings that map its own concepts to vendor locators.

An Environment shall be bindable to a Deployment Platform locator.

For Kubernetes that locator identifies a cluster and a namespace.

An Application shall be bindable to an image reference together with a pattern that yields the Application Version from the image tag.

The pattern is supplied by the user, because only the user knows the team's tagging convention.

External Bindings are user-owned configuration.

They are held outside the Domain Model.

No Kubernetes concept, and no concept belonging to any other vendor, shall appear on Environment, Application or Application Version.

The Domain Model remains exactly what it is today, and adding a second Deployment Platform shall not change it.

A workload that matches no binding shall be reported as unrecognized in the Sync Run.

It shall never be silently dropped, and Tower shall never invent an Application or an Application Version to accommodate it.

Tower's claim is that it reports observed facts with provenance.

Attributing a workload to an Application on a guess would produce a fact with false provenance, which is worse than reporting nothing.

Reporting it as unrecognized tells the user that something is running which Tower cannot explain, which is itself useful information.

External Bindings shall not be exported.

ADR-010 already excludes configuration from export files.

Bindings are additionally per-instance, because the cluster a developer can reach is a property of that developer's machine and credentials rather than of the release being prepared.

External Bindings shall never contain credentials.

Where a binding identifies a cluster, the credential for that cluster is obtained through the credentials port and is stored by the configuration module, as ADR-009 and NFR-028 require.

---

## Consequences

Positive

- the Domain Model stays vendor neutral, and CM-03, CM-05, FR-037 and ADR-003 are satisfied by construction;
- adding a second Deployment Platform requires a new Connector and new bindings, and no change to Environments or Applications;
- the team's tagging convention is stated explicitly and visibly rather than assumed by Tower;
- unrecognized workloads surface a real gap in the team's configuration instead of being hidden;
- bindings can be changed without touching any Observation, because Observations already recorded remain facts about what was seen.

Negative

- Tower cannot synchronize anything until a user has configured bindings, so the first run requires setup;
- a wrong pattern produces wrong Application Versions, and because Observations are immutable those Observations remain in the history after the pattern is corrected;
- bindings are per-instance and are not shared by export, so each developer configures their own;
- a further concept must be persisted, presented and validated;
- an image whose tag does not encode a version cannot be mapped at all, and such workloads will remain permanently unrecognized.

---

## Alternatives Considered

### Add Vendor Fields to Environment and Application

Rejected.

This is the smallest change and it breaks the property the architecture exists to protect.

CM-03, CM-05 and FR-037 would all be violated, and ADR-003's vendor neutrality would become nominal.

A second Deployment Platform would then require either more fields or a rewrite of the Domain Model.

---

### Infer the Mapping by Name

Rejected.

Matching a namespace called production to an Environment called Production works until it does not.

Teams name namespaces for tenants, regions and branches, and a silent mismatch would attribute a deployment to the wrong Environment.

An Observation with the wrong Environment is indistinguishable from a correct one once stored, and Observations are immutable.

Convenience at configuration time is not worth a class of error that cannot be detected afterwards.

---

### Discover Applications Automatically From Images

Rejected.

Creating an Application for every unrecognized image would populate Tower with entries nobody asked for.

It would also mean Tower asserting that a thing is an Application of the team's system on no evidence beyond it running somewhere.

Reporting the workload as unrecognized conveys the same information without the false claim.

---

### Hold the Mapping in the Connector Configuration Only

Rejected.

The mapping would then be invisible to the application layer, and the Collector could not report which Environment an Observation belonged to without asking the Connector.

Vendor-specific knowledge would leak upward through the answer instead of terminating at the boundary.

Holding bindings as user-owned configuration keeps the Connector stateless with respect to Tower's concepts, which is what CM-03 asks for.

---

## Related Documents

- Connector-Model.md
- Domain-Model.md
- Information-Architecture.md
- Non-Functional-Requirements.md
- Implementation-Plan.md
- ADR-003-Vendor-Neutral-Connectors.md
- ADR-005-Environments-Are-Shared-Across-Promotion-Paths.md
- ADR-009-Local-First-Deployment.md
- ADR-010-Tower-Owned-Data-Is-Portable.md
- ADR-011-Synchronization-Records-Change-Not-Repetition.md
