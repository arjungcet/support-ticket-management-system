# Support Ticket Management System (SDD)

A support ticket system built with **Specification-Driven Development**: requirements → specification → architecture
→ data model → API contract → state machine → test strategy → implementation plan → code → tests → review → fix.
Every phase leaves a reviewed artifact under `spec/` or `docs/` before code is written. The workflow and its rules
live in [`rules/ai-assisted-development.md`](rules/ai-assisted-development.md).

**Stack:** Java 21 · Spring Boot 4.1 · Gradle · PostgreSQL (H2 for lightweight local runs) · Next.js 16 / React 19 /
TypeScript · JUnit (Jupiter) · Vitest + Testing Library + MSW · Playwright.

## Current status

| Area | State |
|------|-------|
| Specifications | Drafted and reviewed. [Requirements analysis](spec/requirements.md) complete: assumptions, 11 open questions and 6 decisions awaiting sign-off. Phase 0 decisions still open ([spec review](docs/reviews/2026-09-26-spec-review.md)) |
| Backend | Ticket API implemented: domain + state machine, PostgreSQL persistence (JPA + Flyway), REST API, validation, Problem Details errors |
| Frontend | Implemented against the API contract: create, list, details, edit, assignee, comments, search, filter, status transitions, error handling |
| Tests | Backend: 80 unit (incl. ArchUnit) + 267 integration tests on PostgreSQL (Testcontainers), coverage gate ≥ 80 % line / 70 % branch. Frontend: 83. E2E: 21/21 against the real backend on PostgreSQL |
| Acceptance | **Accepted: 15/15 criteria PASS** ([re-run](docs/reviews/2026-09-26-acceptance-review-2.md)), with open conditions: auth/deployment decision, Phase 0 spec decisions, CI. Earlier run: [acceptance review](docs/reviews/2026-09-26-acceptance-review.md) |

## Repository layout

```
backend/            Spring Boot service (Gradle, Kotlin DSL)
  src/main/           application code
  src/test/           unit and slice tests (./gradlew test)
  src/integrationTest/ integration tests incl. spec-derived API tests (./gradlew integrationTest)
frontend/           Next.js app
  src/                app routes, features, API client, UI components
  tests/              Vitest tests mirroring src/, plus tests/support (MSW, fixtures, setup)
e2e/                Playwright end-to-end journeys and a contract stub used to validate the tests
spec/               requirements, architecture, data model, API contract, state machine, test strategy, plan
rules/              binding engineering rules (Java/Spring, API, testing, security, AI-assisted workflow)
skills/             reusable AI skills (documentation standards and templates)
commands/           reusable AI commands: review-spec, generate-tests, review-code
docs/               prompt history, AI-output audit, ADRs, review reports
.specstory/history/ full text of every prompt
AGENTS.md, CLAUDE.md, .cursor/, .claude/   entry points and adapters for AI coding tools
```

## Prerequisites

- JDK 21 (the Gradle toolchain looks for it; set `JAVA_HOME` or `JAVA21_HOME` if it isn't found)
- Node.js ≥ 22.13 and npm
- Docker: required for the backend integration tests (Testcontainers), E2E (throwaway PostgreSQL) and local PostgreSQL

## Running

```bash
# Local PostgreSQL (copy .env.example to .env first)
docker compose up -d db

# Backend (http://localhost:8080) with PostgreSQL: export SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD (see .env.example)
cd backend && ./gradlew bootRun

# Backend without a database server (in-memory, data lost on restart)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=h2'

# Frontend (http://localhost:3000). Proxies /api/* to BACKEND_URL at runtime (defaults to :8080 in dev)
cd frontend && npm install && npm run dev

# Frontend production build against another backend
cd frontend && npm run build && BACKEND_URL=http://backend.example:8080 npm start
```

## Testing

```bash
cd backend && ./gradlew build                 # unit + ArchUnit + integration tests on PostgreSQL (Docker), coverage gate
cd frontend && npm test && npm run lint && npm run typecheck
cd backend && ./gradlew bootJar && cd ../frontend && npm run build   # E2E needs both builds
cd e2e && npm install && npx playwright install chromium
cd e2e && npm run e2e                          # real backend on a throwaway PostgreSQL container (Docker)
cd e2e && E2E_DB=h2 npm run e2e               # real backend on in-memory H2 (J13 persistence then fails by design)
cd e2e && npm run e2e:stub                     # against the contract stub (validates the tests themselves)
```

## Documentation

| Topic | Where |
|-------|-------|
| Requirements and design | [`spec/`](spec/): start with [`requirements.md`](spec/requirements.md) and [`architecture.md`](spec/architecture.md) |
| API contract and state machine | [`spec/api-contract.md`](spec/api-contract.md), [`spec/state-machine.md`](spec/state-machine.md) |
| Test strategy and implementation plan | [`spec/test-strategy.md`](spec/test-strategy.md), [`spec/implementation-plan.md`](spec/implementation-plan.md) |
| Engineering rules | [`rules/`](rules/) |
| Decisions | [`docs/adr/`](docs/adr/) |
| Reviews (spec, code, security, E2E, acceptance, fixes) | [`docs/reviews/`](docs/reviews/) |
| AI-output audit | [`docs/ai-review.md`](docs/ai-review.md) |
| AI development history (tools, prompts, results, human decisions per phase) | [`docs/ai-development-history.md`](docs/ai-development-history.md) |
| Prompt history | [`docs/prompt-history.md`](docs/prompt-history.md), [`.specstory/history/`](.specstory/history/) |

No secrets belong in this repository. Configuration comes from environment variables. See
[`rules/security.md`](rules/security.md) and `frontend/.env.example`.
