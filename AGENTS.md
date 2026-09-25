# AGENTS.md — AI engineering guide (entry point)

Tool-agnostic entry point for every AI coding agent working in this repository (Claude Code, Cursor, others).
Read this first, then the files it links to.

## Project

**Support Ticket Management System** — Java 21 / Spring Boot REST backend, PostgreSQL (production),
H2 (lightweight local/test only), React + Next.js frontend. Built with **Specification-Driven Development**.

> Status: backend ticket API (PostgreSQL), frontend and E2E suite implemented and passing; CI in
> `.github/workflows/ci.yml`. Requirements signed off and all decisions made. Latest verdicts are in `docs/reviews/`.

## Non-negotiables

1. **Follow the SDD phase order** in [`rules/ai-assisted-development.md`](rules/ai-assisted-development.md) §1.
   Do only the phase requested; if an earlier artifact is missing, say so instead of jumping ahead.
2. **Log every user prompt** to `.specstory/history/` and `docs/prompt-history.md` (§2 of the same file). Redact secrets.
3. **Never invent behaviour** the spec doesn't define — add it to the doc's *Open questions* and ask.
4. **Verify AI-generated code** with the checklist in §3 before claiming it's done. Report what you ran and what you didn't.
5. **No secrets** anywhere in the repo — [`rules/security.md`](rules/security.md).

## Rules (binding)

| File | Covers |
|------|--------|
| [`rules/java-springboot.md`](rules/java-springboot.md) | Java 21 conventions, package layout, layering & dependency direction, validation, exception handling, DB practices, transactions, build |
| [`rules/api-standards.md`](rules/api-standards.md) | REST conventions, pagination/search/filter, DTOs, RFC 9457 error format, frontend error handling |
| [`rules/testing.md`](rules/testing.md) | Test pyramid, mandatory coverage (state machine matrix, validation, errors), H2 vs Testcontainers, frontend tests |
| [`rules/security.md`](rules/security.md) | Secrets, input handling, logging, CORS, dependencies |
| [`rules/ai-assisted-development.md`](rules/ai-assisted-development.md) | SDD phase order, prompt history, AI-code verification, code-review expectations |

## Skills

| Skill | Use when |
|-------|----------|
| [`skills/documentation/SKILL.md`](skills/documentation/SKILL.md) | Creating/updating any document under `docs/` (templates in `skills/documentation/templates/`) |

## Commands

| Command | Purpose |
|---------|---------|
| [`commands/review-spec.md`](commands/review-spec.md) | Review a spec/contract/data-model/state-machine doc before it drives implementation |
| [`commands/generate-tests.md`](commands/generate-tests.md) | Generate spec-derived tests for a feature, plan step, or class |
| [`commands/review-code.md`](commands/review-code.md) | Review code changes against spec + rules; writes `docs/reviews/…` |

## Repository layout (target)

```
AGENTS.md / CLAUDE.md        # agent entry points
rules/ skills/ commands/     # canonical AI guidance (tool-agnostic)
.cursor/rules/*.mdc          # Cursor adapters → point at rules/
.github/copilot-instructions.md  # GitHub Copilot adapter → points at AGENTS.md
.cursor/commands, .claude/   # symlinks → commands/, skills/
spec/                        # SDD phase artifacts (requirements, functional spec, architecture, data model, openapi, state machine, test strategy, plan)
docs/                        # supporting docs (adr/, reviews/, prompt-history.md)
.specstory/history/          # full prompt transcripts
backend/                     # Spring Boot (Gradle, Kotlin DSL): src/main, src/test, src/integrationTest
frontend/                    # Next.js: src/ (app code), tests/ (Vitest, mirrors src/)
e2e/                         # Playwright journeys + contract stub
README.md                    # human entry point: status, layout, how to run and test
```

Edit guidance **only** in `rules/`, `skills/`, `commands/`. The `.cursor/` and `.claude/` entries are thin adapters/symlinks.
