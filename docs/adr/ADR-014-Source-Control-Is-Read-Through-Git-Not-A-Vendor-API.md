# ADR-014 — Source Control Is Read Through Git, Not A Vendor API

**Status**

Accepted

---

## Context

Issue #3 asks for one implementation behind the Source Control Connector category, and names Bitbucket, GitHub and GitLab as the candidates.

Choosing between them looked like the first decision to take. It is not, because the information the Connector needs is not vendor information.

Connector-Model.md lists what a Source Control Connector provides: repositories, branches, tags, commits, release branches.

Every one of those is a git concept. None of them is a GitHub concept, a GitLab concept or a Bitbucket concept.

The three vendors differ in what they add *around* git — pull requests, pipelines, permissions, issue links — and Tower needs none of that to register an Application Version.

`ApplicationVersion` carries branch, tag and commit as optional identifying attributes. Those are exactly the fields git refs already answer.

There is a second consideration, and it is the stronger one.

ADR-001 makes Tower read-only against every External System, and issue #3 restates it: Tower never pushes, never tags, never opens a pull request.

A vendor REST API is authenticated with a token that can do all three. The read-only guarantee would rest entirely on Tower choosing not to call those endpoints — enforced by an architecture test that checks method names, which ADR-001's own record admits makes the obvious mistake impossible rather than the determined one.

The git wire protocol is different in kind. Reference discovery — what `git ls-remote` performs — has no write counterpart in the same exchange. A connector that only ever discovers refs cannot push, because pushing is a different operation the connector never invokes and the protocol will not smuggle.

A third consideration is smaller but real. ADR-003 requires connectors to be vendor-neutral, and a second implementation of the SPI is the only genuine test of whether that neutrality holds or whether the SPI came out Kubernetes-shaped. A connector that is neutral *across* the three candidate vendors tests it harder than one that picks a favourite.

---

## Decision

The Source Control Connector shall read refs over the git protocol rather than through any vendor's REST API.

One implementation, `tower-connector-git`, shall serve GitHub, GitLab, Bitbucket, a self-hosted server and a bare repository on a file share alike, because all of them speak git.

The Connector shall perform reference discovery only. It shall not clone, fetch objects, or read file content.

A ref shall be reported as a **branch** or a **tag** with the commit it resolves to, and nothing else. No vendor field, no pull request, no pipeline.

Annotated tags shall be reported as the commit they point at rather than as the tag object, because the commit is what identifies the code and the tag object is a git implementation detail.

**Build identifier shall remain absent.** Issue #3 lists it among the attributes to populate, and git cannot supply it: a build identifier is produced by a build system, not recorded in a ref. Tower shall leave it empty rather than derive something plausible from a commit, which is the same rule the renderers follow — absent information is stated, never invented.

---

## Consequences

One connector covers all three candidate vendors, and the choice between them stops being an architectural decision. It becomes a URL.

The read-only guarantee gains a structural basis rather than resting on naming discipline alone. This is the first connector where "it cannot write" is a property of the protocol and not only of the code.

`ApplicationVersion` cannot acquire a vendor-shaped field from this Connector, because the Connector has no vendor model to leak. Issue #3's constraint is satisfied by construction rather than by review.

The Connector can be tested without a network and without credentials, because a local bare repository is a valid git remote. That is worth stating plainly: the Kubernetes connector needed a mock server, and this one needs a directory.

What Tower gives up is everything a vendor API adds. Pull requests, build results, review state and issue links are not available through this Connector and will need a different one if they are ever wanted — Connector-Model.md already anticipates an Issue Tracking Connector for part of that.

Build identifier stays unpopulated until something that knows about builds can supply it. A user may still enter one by hand, because a discovered version is a candidate the user accepts rather than a fact imposed on them.

Discovery does not create Application Versions on its own. BR-01 makes an Application Version immutable, so a Connector may surface a version Tower has not seen and may never revise one Tower already recorded. Whether a candidate becomes an Application Version is the user's decision, and a rediscovered ref for a version already held is not a change.
