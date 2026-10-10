---
paths:
  - "docs/**"
  - "Eta_doc/**"
  - ".claude/rules/**"
  - "scripts/documentation.py"
  - "zensical.toml"
  - "requirements-docs.txt"
  - "README.md"
  - ".github/workflows/documentation.yml"
  - ".github/pull_request_template.md"
  - "scripts/tests/**"
  - "docs/documentation-map.json"
---

# Documentation changes

Follow [`docs/workflow.md`](../../docs/workflow.md) and the topic mappings in [`docs/documentation-map.json`](../../docs/documentation-map.json). Keep existing canonical sources in place; distinguish requirements, implemented behavior, and historical notes. Update canonical documentation with behavior changes, never edit generated projections, and record a specific reason when a change needs no documentation update. Check claims against source code; structural checks do not prove meaning or runtime behavior. Do not include secrets or private data.

The source check uses Python's standard library; the strict Zensical build also
validates rendered links and anchors. Tool regression tests cover link projection,
code examples, repository boundaries, review-topic precedence and schema mismatch.
Browser verification must exercise navigation, search, historical warnings,
embedded assets and the Markdown/llms.txt access paths.
