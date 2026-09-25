# Backend Implementation Report — Milestones 09–11 — 2026-09-26

| Scope | Specs implemented | Status |
|-------|-------------------|--------|
| 09 backend domain, 10 backend API, 11 backend tests | `spec/requirements.md` REQ-1…12, `api-contract.md`, `state-machine.md`, `data-model.md` §3–§13, `architecture.md` §5–§18 | Backend build green on **PostgreSQL** (Testcontainers): 80 unit + 267 integration tests. E2E **21/21** against the real backend on PostgreSQL |

## What was built

| Layer | Files (`backend/src/main/…`) | Notes |
|-------|-----------------------------|-------|
| Schema | `db/migration/common/V1__create_ticket.sql`, `V2__create_ticket_comment.sql` | Portable SQL. Every constraint from data-model §10, including `ck_ticket_status_timestamps`. Indexes from §9 |
| Domain (09) | `ticket/domain/Ticket`, `Comment`, `TicketStatus`, `TicketPriority`, `FieldLimits`, `TicketSearchCriteria`, `TicketExceptions` | **State machine in the domain** (`TicketStatus` table + `Ticket.changeStatus`, the only status mutator). Rules are checked before any change. Terminal tickets frozen (A-17/A-18). `updatedAt` never moves backwards (SR-16) |
| Persistence | `ticket/persistence/TicketRepository`, `TicketSearchQueries(Impl)`, `TicketSummaryRow`, `CommentRepository`, `TicketSpecifications` | List pages are a projection: one content query + one count query, no entity loads. Bound parameters only. `LIKE … ESCAPE '\'` with escaped wildcards. Priority/status ordered by rank (`CASE`) with `id` tie-breaker |
| Application | `ticket/application/TicketService`, `TicketCommentService`, `TicketCommands`, `TicketViews` | One use case = one transaction. Version checked before mutation. Flush so the response carries the new version. Commit-time conflicts → 409 |
| API (10) | `ticket/api/TicketController`, `TicketCommentController`, `JsonBody`, `RequestParams`, `Validation`, `TicketResponses` | All 9 endpoints + the comment list. All field errors reported together, precedence per contract §1.3, relative `Location` |
| Cross-cutting | `shared/error/*`, `shared/web/CorrelationIdFilter`, `shared/config/ClockConfig` | Single Problem Details producer. No internals leaked. Correlation id on every response. µs-truncated UTC clock |
| Config | `application.yml` (datasource from `SPRING_DATASOURCE_*`, embedded fallback disabled → fail fast), `application-h2.yml`, root `docker-compose.yml` + `.env.example` | `ddl-auto=validate`, OSIV off, UTC, stack traces never exposed |

## Tests (11)

| Suite | Count | Result |
|-------|-------|--------|
| Unit (`src/test`): `TicketStatusTest` (25-pair matrix), `TicketTest` (A1–A5, all 20 rejections leave the ticket unchanged, edit/assign/comment rules, clock skew), `TicketSearchCriteriaTest`, `ArchitectureTest` (7 ArchUnit rules), `EmbeddedTomcatVersionTest`, application class | 80 | ✅ |
| Integration on PostgreSQL 17 (Testcontainers): 241 spec-derived API tests, `DatabaseIT` (every constraint, FK restrict, identity, timestamp round-trip in a non-UTC JVM, optimistic lock with two concurrent transactions, projection query count), `PersistenceRestartIT` (two app contexts, same DB: data, versions, comments, Flyway history, identity, lifecycle continue), context load | 266 | ✅ |
| Integration on the `h2` profile: `H2ProfileSmokeIT` (portability smoke only) | 1 | ✅ |
| Coverage gate (JaCoCo, both suites): `ticket.domain` 99.3 % line / 90.4 % branch, `ticket.application` 100 % / 100 % (floor 80 / 70) | — | ✅ |
| E2E, real backend on a throwaway PostgreSQL container | 21 | ✅ 21, including J13 persistence after restart |
| E2E vs contract stub | 21 | ✅ |

**Evidence the tests can fail:**
- Allowing `CLOSED → OPEN` in `TicketStatus` made the HTTP state-machine tests fail (reverted).
- One new `DatabaseIT` case failed on the first run. It was a **test defect**: PostgreSQL rejects an explicit id into a `GENERATED ALWAYS` column with SQLSTATE 428C9, which Spring maps to `BadSqlGrammarException`, not `DataIntegrityViolationException`. The assertion now checks the rejection reason itself.

## Resolved since the first report

| Earlier deviation | Resolution |
|-------------------|------------|
| D-1 integration tests on H2 | Docker started. All database-touching integration tests now run on PostgreSQL via Testcontainers. H2 keeps one smoke test. No silent fallback: without Docker the suite fails |
| D-2 E2E J13 fails | The E2E harness starts a throwaway PostgreSQL container (per-run random password, removed afterwards). J13 passes. `E2E_DB=h2` remains available, explicitly |
| D-4 list loads entities | Projection query. `DatabaseIT` asserts 2 statements and 0 entity loads per page |
| D-5 ArchUnit, restart tests, coverage gate, repository/constraint tests | Implemented (see above) |
| Security L-4 (no root `.env.example`) | Added, with `docker-compose.yml` for local PostgreSQL |

## Still open (blocked on specification decisions, not implemented on purpose)

| # | Item | Why not now |
|---|------|-------------|
| O-1 | Bean Validation vs `JsonBody` (`architecture.md` §11 now carries an implementation note) | Depends on Phase 0 STEP-02 contract semantics |
| O-2 | Request-size limit / `406` / `413` (plan STEP-34) | Those error codes aren't in the API contract yet (review SR-23). Adding them would invent API behaviour |
| O-3 | `PESSIMISTIC_READ` when adding comments (review SR-08) | A proposed spec change, not yet approved |
| O-4 | Deterministic service-level race hook (plan STEP-41) | The commit-time optimistic lock is proven at database level (`DatabaseIT`), and the API-level outcome by `StatusTransitionApiIT`. A production test hook needs the STEP-03 spec decision |
| O-5 | Phase 0 overall (functional spec, `openapi.yaml`, pinned-version ADR) | Engineer decision |
