---
name: documentation
description: Write and maintain SDD documentation for this project (requirements, functional spec, architecture, ADRs, data model, API contract, state machine, test strategy, implementation plan, reviews). Use whenever a document under spec/ or docs/ is created or changed.
---

# Documentation Skill

Use this skill whenever you create or update a phase artifact under `spec/` or a supporting document under `docs/`. It defines **where** each document lives,
**what** it must contain, and **how** it is written. Templates are in `skills/documentation/templates/`.

## 1. Document map

| Phase | File | Template |
|-------|------|----------|
| Requirement | `spec/requirements.md` | — (list of numbered requirements `REQ-n`) |
| Specification | `spec/functional-spec.md` | `templates/spec-template.md` |
| Architecture | `spec/architecture.md` | — (C4 context + container + component views) |
| Decisions | `docs/adr/NNNN-title.md` | `templates/adr-template.md` |
| Data model | `spec/data-model.md` | — (ER diagram + table definitions + constraints) |
| API contract | `spec/api-contract.md` | — (normative; see `rules/api-standards.md`) |
| API contract (machine-readable) | `spec/openapi.yaml` | — (OpenAPI 3.1, derived from `api-contract.md`) |
| State machine | `spec/state-machine.md` | — (diagram + full transition table) |
| Test strategy | `spec/test-strategy.md` | — (see `rules/testing.md`) |
| Implementation plan | `spec/implementation-plan.md` | — (ordered, independently verifiable steps) |
| Review | `docs/reviews/YYYY-MM-DD-<scope>.md` | output of `commands/review-code.md` |
| Prompt log | `docs/prompt-history.md` | see `rules/ai-assisted-development.md` §2 |

## 2. Writing standards

- **Audience**: a developer new to the project, and an AI agent with no prior context. Be explicit.
- **Header block** on every doc: title, status (`Draft` / `In review` / `Approved` / `Superseded`), last updated date,
  related documents.
- **Traceability IDs**: requirements `REQ-n`, acceptance criteria `AC-n.m`, business rules `BR-n`, decisions `ADR-NNNN`,
  plan steps `STEP-n`. Later documents reference earlier IDs; never renumber an approved ID.
- **Testable language**: use MUST / MUST NOT / SHOULD / MAY (RFC 2119). Every MUST is verifiable by a test.
  Avoid "fast", "user-friendly", "etc." — give numbers and exhaustive lists.
- **Acceptance criteria** in Given / When / Then form.
- **Diagrams as code** (Mermaid) so they diff in PRs. No binary images for things that change.
- **Open questions** section at the end of each doc; an item stays open until the user answers — agents do not guess.
- **Assumptions** listed explicitly and flagged for confirmation.
- Keep one source of truth: link instead of copying (e.g. field limits are defined in the data model; the spec links to them).
- Markdown: one H1 per file, sentence-case headings, tables for enumerable facts, fenced code blocks with language tags.

## 3. Code-level documentation

- Javadoc on public types and on public methods of `application` services and `domain` classes whose behaviour isn't
  obvious from the name. Explain *why* and invariants, not *what* the code literally does.
- No Javadoc noise on getters, DTO records, or trivial methods.
- Comments that reference a rule cite its id: `// BR-3: terminal states cannot transition`.
- `README.md` at repo root: what it is, prerequisites, how to run backend/frontend/tests, links to `docs/`.
- `backend/README.md` and `frontend/README.md` for module-specific setup once those modules exist.

## 4. Updating documents

- When code changes behaviour, the affected doc is updated **in the same PR**.
- Approved docs are changed by editing + bumping "last updated" + a short changelog line at the bottom;
  significant decisions get a new ADR that supersedes the old one (never delete ADRs).
- Run `commands/review-spec.md` on any spec/contract change before implementation starts.
