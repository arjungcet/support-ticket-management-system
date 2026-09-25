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
| Specifications | Reviewed. [Requirements](spec/requirements.md) signed off by the product owner (assumptions confirmed as built, Q-1…Q-11 answered). Machine-readable contract: [`spec/openapi.yaml`](spec/openapi.yaml). Still open: decisions D-2, D-4, D-5 |
| Backend | Ticket API implemented: domain + state machine, PostgreSQL persistence (JPA + Flyway), REST API, validation, Problem Details errors |
| Frontend | Implemented against the API contract: create, list, details, edit, assignee, comments, search, filter, status transitions, error handling. Status/priority badges, dark mode, nonce-based Content-Security-Policy and security headers |
| Tests | Backend: 80 unit (incl. ArchUnit) + 267 integration tests on PostgreSQL (Testcontainers), coverage gate ≥ 80 % line / 70 % branch. Frontend: 95. E2E: 24/24 against the real backend on PostgreSQL (incl. security headers) |
| Acceptance | **Accepted: 15/15 criteria PASS** ([re-run](docs/reviews/2026-09-26-acceptance-review-2.md)), Since then: deployment boundary decided ([ADR-0002](docs/adr/0002-no-authentication-internal-deployment.md): no login, local/internal networks only) and security headers added. Still open: CI, decisions D-2/D-4/D-5. Earlier run: [acceptance review](docs/reviews/2026-09-26-acceptance-review.md) |

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
AGENTS.md, CLAUDE.md, .cursor/, .claude/, .github/copilot-instructions.md   entry points and adapters for AI coding tools
```

## Prerequisites

- A JDK 17+ to start Gradle. The build compiles and runs on JDK 21: an installed JDK 21 is used, otherwise Gradle
  downloads one automatically (foojay toolchain resolver)
- Node.js ≥ 22.13 and npm
- Docker: required for the backend integration tests (Testcontainers), E2E (throwaway PostgreSQL) and local PostgreSQL

## Running

Three terminals, from the repository root. Only Docker, a JDK and Node are needed.

```bash
# 1. Local PostgreSQL. Edit .env: set a password (and POSTGRES_PORT if 5432 is already taken, see Troubleshooting)
cp .env.example .env
docker compose up -d db
```

```bash
# 2. Backend on http://localhost:8080 (loads the database settings from .env into the environment)
set -a && . ./.env && set +a && cd backend && ./gradlew bootRun
```

```bash
# 3. Frontend on http://localhost:3000 (proxies /api/* to BACKEND_URL, default http://localhost:8080)
cd frontend && npm ci && npm run dev
```

Open http://localhost:3000. Data is kept in the `db-data` Docker volume across restarts
(`docker compose down -v` deletes it).

Other ways to run:

```bash
# Backend without a database server (in-memory H2, data lost on restart): skip step 1
cd backend && ./gradlew bootRun --args='--spring.profiles.active=h2'

# Frontend production build against another backend
cd frontend && npm run build && BACKEND_URL=http://backend.example:8080 npm start
```

### Troubleshooting

| Symptom | Fix |
|---------|-----|
| `docker compose up`: `bind: address already in use` on 5432 | A local PostgreSQL already uses 5432. In `.env` set `POSTGRES_PORT=5433` and change `SPRING_DATASOURCE_URL` to `…localhost:5433/…` |
| Backend exits with `Failed to configure a DataSource: 'url' attribute is not specified` | The `SPRING_DATASOURCE_*` variables aren't set in that terminal: run step 2 exactly as shown, or use the `h2` profile |
| Backend: `password authentication failed` | `.env` password changed after the volume was created. Use the original, or `docker compose down -v` and start again |
| Integration tests / E2E fail with `Could not find a valid Docker environment` | Start Docker (Testcontainers and the E2E database need it) |
| Port 8080 or 3000 in use | Stop the other process, or `SERVER_PORT=8081 ./gradlew bootRun` and `BACKEND_URL=http://localhost:8081 npm run dev -- -p 3001` |

## Testing

```bash
cd backend && ./gradlew build                 # unit + ArchUnit + integration tests on PostgreSQL (Docker), coverage gate
cd frontend && npm test && npm run lint && npm run typecheck
cd backend && ./gradlew bootJar && cd ../frontend && npm run build   # E2E needs both builds
cd e2e && npm ci && npx playwright install chromium
cd e2e && npm run e2e                          # real backend on a throwaway PostgreSQL container (Docker)
cd e2e && E2E_DB=h2 npm run e2e               # real backend on in-memory H2 (J13 persistence then fails by design)
cd e2e && npm run e2e:stub                     # against the contract stub (validates the tests themselves)
```

## Documentation

| Topic | Where |
|-------|-------|
| Requirements and design | [`spec/`](spec/): start with [`requirements.md`](spec/requirements.md) and [`architecture.md`](spec/architecture.md) |
| API contract and state machine | [`spec/api-contract.md`](spec/api-contract.md), [`spec/openapi.yaml`](spec/openapi.yaml) (OpenAPI 3.1), [`spec/state-machine.md`](spec/state-machine.md) |
| Test strategy and implementation plan | [`spec/test-strategy.md`](spec/test-strategy.md), [`spec/implementation-plan.md`](spec/implementation-plan.md) |
| Engineering rules | [`rules/`](rules/) |
| Decisions | [`docs/adr/`](docs/adr/): PostgreSQL (0001), no authentication / internal deployment only (0002) |
| Reviews (spec, code, security, E2E, acceptance, fixes) | [`docs/reviews/`](docs/reviews/) |
| AI-output audit | [`docs/ai-review.md`](docs/ai-review.md) |
| AI development history (tools, prompts, results, human decisions per phase) | [`docs/ai-development-history.md`](docs/ai-development-history.md) |
| Prompt history | [`docs/prompt-history.md`](docs/prompt-history.md), [`.specstory/history/`](.specstory/history/) |

No secrets belong in this repository. Configuration comes from environment variables. See
[`rules/security.md`](rules/security.md) and `frontend/.env.example`.
