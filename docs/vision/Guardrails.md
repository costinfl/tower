# Guardrails

This document defines what Tower must never become.

---

# Tower is NOT

- A CI/CD platform
- A deployment engine
- A Kubernetes management tool
- A Release Management system
- A project management platform
- A replacement for Jira
- A replacement for Jenkins
- A replacement for Git
- A replacement for OpenShift
- A monitoring platform

---

# Tower ALWAYS

- Observes
- Correlates
- Documents
- Explains
- Visualizes

---

# Tower NEVER

- Executes deployments
- Modifies Kubernetes resources
- Creates or updates Jira issues automatically
- Pushes commits
- Replaces existing engineering tools

---

# Feature Evaluation

Every new feature proposal should answer:

- Does it improve release understanding?
- Does it answer one of Tower's core questions?
- Can it remain read-only?
- Does it avoid duplicating another tool?

If any answer is negative, the feature should be reconsidered.
