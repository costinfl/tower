# Vendored API descriptions

These are not Tower's specifications. They are slices of the descriptions the vendors publish for
their own APIs, committed so that the fixtures in the Connector tests can be checked against
something Tower did not write.

ADR-019 records why. In short: a fixture I wrote by hand and a Connector I wrote by hand share my
beliefs about the vendor, so a green test proves they agree with each other and nothing else. A
vendor's own description is the cheapest independent opinion available.

**What this does not establish.** A description is not a server. Vendors' descriptions drift from
their implementations, and none of this proves that the real API answers the way its own document
says it does. That is what the live checks in `docs/CHECKLIST.md` are for, and they stay outstanding
regardless of how green this is.

---

## What is in here

| File | Upstream | Version | Licence |
|---|---|---|---|
| `github/api.github.com.slice.json` | [github/rest-api-description](https://github.com/github/rest-api-description) | `info.version` 1.1.4, fetched 2026-08-02 | MIT, stated in the file's own `info.license` |
| `jira/` | not present — see below | | |

Each file is a **slice**, not the whole description. GitHub's full description is 12.9 MB, which
would dwarf this repository's own source and make every upstream edit an unreadable diff. The slice
carries the two endpoints Tower reads and everything they reference: 96 KB, 22 schemas.

Two properties of a slice are deliberate:

- **GET only.** `slice-openapi.py` refuses a path with no GET and keeps no other method, so a
  vendored description here cannot describe a write even in principle (ADR-001).
- **Deterministic.** Sorted keys, fixed indentation, one trailing newline, and no timestamp in the
  provenance block. Re-slicing the same upstream produces a byte-identical file, so any diff means
  the vendor changed something. This is the property NFR-025 asks of generated documents, for the
  same reason.

Examples are stripped. They are documentation, they validate nothing, and for GitHub they were 39 KB
of a 106 KB file.

---

## Regenerating

### GitHub

```
python3 scripts/slice-openapi.py \
  --from https://raw.githubusercontent.com/github/rest-api-description/main/descriptions/api.github.com/api.github.com.json \
  --path '/repos/{owner}/{repo}' \
  --path '/repos/{owner}/{repo}/issues/{issue_number}' \
  --title 'GitHub REST API — the slice Tower reads' \
  --out specs/github/api.github.com.slice.json
```

### Jira

**Not committed, and it needs you rather than an agent.** `developer.atlassian.com` is unreachable
from the environment Tower is developed in — blocked by the same network policy that blocks Jira
itself — so the slice has never been produced here.

```
python3 scripts/slice-openapi.py \
  --from https://developer.atlassian.com/cloud/jira/platform/swagger-v3.v3.json \
  --path '/rest/api/3/issue/{issueIdOrKey}' \
  --path '/rest/api/3/serverInfo' \
  --path '/rest/api/3/myself' \
  --title 'Jira Cloud REST API — the slice Tower reads' \
  --out specs/jira/jira-cloud.slice.json
```

Two things to know before you run it.

**The version numbers do not match, on purpose.** Atlassian publishes a description of **v3**; the
Connector reads **v2** (ADR-018 — v2 is the one path Cloud, Server and Data Center all serve, and
Tower should not have to ask which kind of Jira it is talking to). The two versions differ in how
they render rich text: v3 returns Atlassian Document Format where v2 returns a string. Tower reads
neither — it reads `summary` and `status`, which are the same shape in both — so the v3 schema is a
valid check on a v2 fixture. If Tower ever reads a description or a comment, this stops being true
and the slice stops being the right thing to validate against.

**Check the terms before committing it.** GitHub's description states MIT in the file itself.
Atlassian's redistribution terms were not readable from the environment this was set up in, and
nobody has checked them. If the file cannot be vendored, leave `specs/jira/` empty: the schema tests
skip with a message naming what is unverified, which is the honest outcome and not a failure.

---

## Drift

`scripts/check-openapi-drift.sh` re-slices from upstream and diffs against what is committed.

It is **not** part of the build, and that is deliberate. `scripts/acceptance.sh` was kept
network-free for the same reason: a build that goes red because GitHub edited its description is a
build that went red for something the diff did not do. Refreshing a slice should be a decision
somebody makes, and a diff they read.
