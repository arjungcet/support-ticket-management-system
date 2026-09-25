# Final Acceptance Review (re-run) — 2026-09-26

| Baseline | Source of truth | Code changed during review | Previous review |
|----------|-----------------|----------------------------|-----------------|
| Commit `475297b` on `main` (working tree: prompt log only) | `spec/requirements.md` (REQ-1…12, TC-1…5) and the 15 acceptance criteria | No | [`2026-09-26-acceptance-review.md`](2026-09-26-acceptance-review.md): 1 PASS / 1 PARTIAL / 13 FAIL |

## Verdict: **ACCEPTED** against the 15 acceptance criteria, with conditions

| PASS | PARTIAL | FAIL | NOT VERIFIED |
|------|---------|------|--------------|
| **15** | 0 | 0 | 0 |

Every criterion is backed by an automated test that passed **against the real system** during this review:
- the browser, the Next.js production build, the runtime `/api` proxy and the Spring Boot jar, backed by PostgreSQL 17
- backend integration tests on PostgreSQL via Testcontainers

The contract stub was **not** used as evidence. The conditions (§4) aren't acceptance criteria, but they are open and must stay visible.

---

## 1. Test runs performed for this review

| # | Suite | Command | Result |
|---|-------|---------|--------|
| R1 | Backend unit + architecture | `backend/ ./gradlew clean build` | ✅ **80/80**: `TicketStatusTest` 27, `TicketTest` 39, `ArchitectureTest` 7, `TicketSearchCriteriaTest` 5, `EmbeddedTomcatVersionTest` 1, app 1 |
| R2 | Backend integration on **PostgreSQL 17** (Testcontainers), same run | (part of R1) | ✅ **267/267**: 241 spec-derived API tests, `DatabaseIT` 23, `PersistenceRestartIT` 1, context load 1, `H2ProfileSmokeIT` 1 (H2 portability only) |
| R3 | Coverage gate (JaCoCo, both suites) | (part of R1) | ✅ passed. `ticket.domain` 99.3 % line / 90.4 % branch. `ticket.application` 100 % / 100 % (floor 80 / 70) |
| R4 | Frontend | `frontend/ npx vitest run`, `lint`, `typecheck`, `build` | ✅ **83/83**, lint ✅, typecheck ✅, production build ✅ |
| R5 | E2E, real stack (Chromium → Next.js prod build → runtime proxy → backend jar → throwaway PostgreSQL 17 container) | `e2e/ npx playwright test` | ✅ **21/21** (13 journeys). The container was removed afterwards |
| R6 | Secret scan | `git grep` patterns over 213 tracked files, `git log -p --all` (9 commits), tracked `.env`/`.pem`/`.key` | ✅ 0 / 0 / 0 |
| R7 | Environment | `docker info` | ✅ Docker 29.2.1 (Docker Desktop was started for this work; it was stopped in the first review) |

---

## 2. Acceptance criteria

| # | Criterion | Result | Evidence (all from this review's runs) |
|---|-----------|--------|----------------------------------------|
| AC-1 | Ticket can be created from UI | **PASS** | R5 J1: the form creates an OPEN ticket and shows its details (priority, assignee, description). R2 `TicketCreationApiIT` 12/12 (201, relative `Location`, full representation, defaults, trimming). R4 `CreateTicketForm.test.tsx` 6/6 |
| AC-2 | Tickets can be listed | **PASS** | R5 J2: newly created tickets are listed newest first, with a page summary. R2 `TicketListingApiIT` 10/10 (summary shape, paging, rank sorting, empty page). R2 `DatabaseIT` list projection (2 statements, 0 entity loads) |
| AC-3 | Ticket details can be viewed | **PASS** | R5 J3: clicking a row opens the details. R2 `TicketRetrievalApiIT` 4/4, `NotFoundApiIT` 7/7 |
| AC-4 | Ticket fields can be updated | **PASS** | R5 J4: the edit is saved and survives a reload. R2 `TicketUpdateApiIT` 10/10 (partial update, no-op, stale version 409, terminal 422). R4 includes the M-1 lost-update regression test |
| AC-5 | Assignee can be changed | **PASS** | R5 J5: assign, then unassign, survives a reload. R2 `AssigneeApiIT` 12/12 |
| AC-6 | Comments can be added | **PASS** | R5 J6: the comment appears and survives a reload. R2 `CommentApiIT` 13/13 (201, oldest first, paging, no ticket version change, 422 on terminal tickets) |
| AC-7 | Search works | **PASS** | R5 J7 ×2: case-insensitive search over title and description, kept in the URL, empty state. R2 `TicketSearchApiIT` 18/18 on PostgreSQL, including literal `%`/`_`/`\` with decoys, `café`/`CAFÉ`, and injection strings. R4 includes the M-2 URL-sync regression test |
| AC-8 | Status filter works | **PASS** | R5 J8: checkboxes restrict the list, kept in the URL across reloads. R2 `StatusFilterApiIT` 15/15 |
| AC-9 | Valid status transitions work | **PASS** | R5 J9 ×2: `OPEN → IN_PROGRESS → RESOLVED → CLOSED` and `OPEN → CANCELLED` through the UI. R2 `StatusTransitionApiIT` A1–A5 5/5, full paths 2/2, 25-row matrix. R1 `TicketTest` allowed 8/8 |
| AC-10 | Invalid status transitions are rejected by backend | **PASS** | R5 J10 direct API: `CLOSED/RESOLVED/CANCELLED → OPEN` → `409 TICKET_INVALID_TRANSITION`, ticket unchanged. R2 `StatusTransitionApiIT` R1 `CLOSED→OPEN`, R2 `RESOLVED→OPEN`, R3 `CANCELLED→OPEN`, R4–R8, 20 rejected matrix rows, `status` rejected on every other endpoint. R1 `TicketTest` 20/20 rejected pairs leave the entity unchanged. R1 `ArchitectureTest`: only the domain can reject a transition; `Ticket` has no setters |
| AC-11 | Data survives application restart | **PASS** | R5 J13: ticket, status and comment survive a backend restart on PostgreSQL. R2 `PersistenceRestartIT`: two application contexts on the same database give identical ticket, versions and comments; migrations are not re-applied; ids continue; the lifecycle continues; this holds in a non-UTC JVM time zone |
| AC-12 | Backend validation works | **PASS** | R2 `BackendValidationApiIT` 80/80 (boundaries, `REQUIRED`/`BLANK`/`TOO_LONG`/`INVALID_VALUE`/`UNKNOWN_FIELD`/`NO_CHANGES_REQUESTED`, malformed JSON, 415, precedence, no echo). R5 J11 API case. R2 `DatabaseIT` shows the database constraints back the API up |
| AC-13 | UI shows meaningful errors | **PASS** | R5: J11 client hints; J11 **server** validation (q > 100) shown as "Please correct the highlighted fields."; J12 unknown ticket → "Ticket not found" page; J12 backend down → retryable alert, and recovery; J10 stale tab → "changed by someone else" with Reload, message cleared after reload. R4: every contract error code has a message; field errors appear next to fields; a 5xx shows a reference and never raw text. R2 `ErrorContractApiIT` 8/8 (Problem Details shape, correlation ids, 405) |
| AC-14 | State-machine integration tests pass | **PASS** | R2 `StatusTransitionApiIT` **52/52** on PostgreSQL (was 1/52). R5 J9/J10 5/5 |
| AC-15 | No secrets are committed | **PASS** | R6: 0 matches in 213 tracked files and in the full history, no tracked `.env`/key files. The only credential-like values are the placeholder `change-me` in `.env.example` and the empty password of the in-memory H2 profile. E2E database passwords are generated per run and never written to tracked files |

---

## 3. Traceability matrix

| Requirement | Specification section | Implementation | Automated tests | Current result |
|-------------|-----------------------|----------------|-----------------|----------------|
| **REQ-1** Create a ticket | `api-contract.md` §6.1, §4.1. `data-model.md` §3. `architecture.md` §7 | `Ticket.create`, `TicketService.create`, `TicketController#create`, `V1__create_ticket.sql`. FE `CreateTicketForm` | `TicketCreationApiIT` 12, `BackendValidationApiIT$CreateTicket` 26, `TicketTest`, `CreateTicketForm.test` 6, E2E J1 | **PASS** |
| **REQ-2** List tickets | `api-contract.md` §6.2, §4.2, §4.4. `data-model.md` §9 | `TicketSearchQueriesImpl` (projection), `TicketSpecifications`, `TicketController#list`. FE `TicketListView` | `TicketListingApiIT` 10, `DatabaseIT` (projection), `TicketListView.test`, E2E J2 | **PASS** |
| **REQ-3** View ticket details | `api-contract.md` §6.3, §6.3a | `TicketService.get`, `TicketController#get`, `TicketCommentController#list`. FE `TicketDetailsView` | `TicketRetrievalApiIT` 4, `NotFoundApiIT` 7, `TicketDetailsView.test`, E2E J3 | **PASS** |
| **REQ-4** Update title, description, priority, assignee | `api-contract.md` §6.4, §6.5, §1.2 | `Ticket.updateDetails/assign`, `TicketService.update/assign`, `TicketController#update/#assign`. FE `EditTicketForm`, `AssigneeControl` | `TicketUpdateApiIT` 10, `AssigneeApiIT` 12, `TicketTest` edits, FE edit/assignee tests, E2E J4/J5 | **PASS** |
| **REQ-5** Add comments | `api-contract.md` §6.6. `data-model.md` §4 | `Comment`, `TicketCommentService`, `TicketCommentController`, `V2__create_ticket_comment.sql`. FE `CommentsSection` | `CommentApiIT` 13, `BackendValidationApiIT$Comment` 10, `DatabaseIT` comment constraints, E2E J6 | **PASS** |
| **REQ-6** Search by keyword | `api-contract.md` §6.2.1. `data-model.md` §13 | `TicketSearchCriteria.likePattern`, `TicketSpecifications.matching`. FE search box + URL state | `TicketSearchApiIT` 18 (PostgreSQL), `TicketSearchCriteriaTest` 5, FE search tests, E2E J7 | **PASS** |
| **REQ-7** Filter by status | `api-contract.md` §6.2.2 | `RequestParams.statuses`, `TicketSpecifications`. FE status checkboxes | `StatusFilterApiIT` 15, FE filter test, E2E J8 | **PASS** |
| **REQ-8** Persist data | `data-model.md` §1–§12. ADR-0001 | JPA entities, Flyway V1/V2, PostgreSQL datasource (`SPRING_DATASOURCE_*`, no embedded fallback) | `DatabaseIT` 23, `PersistenceRestartIT`, E2E J13 | **PASS** |
| **REQ-9** Validate input at the backend | `api-contract.md` §2.1–§2.3, §1.3 | `JsonBody`, `RequestParams`, `Validation`, domain invariants, DB constraints | `BackendValidationApiIT` 80, `DatabaseIT` constraints, `TicketTest` invariants | **PASS** |
| **REQ-10** Meaningful errors in the UI | `architecture.md` §6.2. `api-contract.md` §2, §8.1 | `GlobalExceptionHandler`, `CorrelationIdFilter`. FE `errors.ts`, `ErrorAlert`, `formErrors`, `useActionFailure` | `ErrorContractApiIT` 8, FE `errors.test` 20 + component tests, E2E J10–J12 | **PASS** |
| **REQ-11** Allowed transitions | `state-machine.md` §3, §4 | `TicketStatus.allowedTargets`, `Ticket.changeStatus`, `TicketController#changeStatus`. FE `StatusActions` (server-driven) | `TicketStatusTest` 27, `TicketTest` allowed 8, `StatusTransitionApiIT`, E2E J9 | **PASS** |
| **REQ-12** Reject invalid transitions | `state-machine.md` §4, §5, §8 | same as REQ-11, plus `InvalidStatusTransitionException` → 409 | `TicketTest` rejected 20, `StatusTransitionApiIT` rejections and enforcement, `ArchitectureTest`, E2E J10 | **PASS** |
| **TC-1** Java 21, Spring Boot, REST | `architecture.md` §3 | Java 21 toolchain, Spring Boot 4.1.1, REST under `/api/v1` | R1 | **PASS** |
| **TC-2** PostgreSQL (prod), H2 (tests/local) | `data-model.md` §12–§14. ADR-0001 | PostgreSQL 17, `h2` profile | R2 (PostgreSQL), `H2ProfileSmokeIT` | **PASS** |
| **TC-3** React / Next.js | `architecture.md` §6 | Next.js 16 / React 19 | R4 | **PASS** |
| **TC-4** Gradle | `rules/java-springboot.md` §8 | Gradle 9.8.0 wrapper | R1 | **PASS** |
| **TC-5** JUnit 5, Spring Boot Test, Mockito, Testcontainers | `rules/testing.md` §2 | JUnit **Jupiter 6.0.3** (Spring Boot BOM), Spring Boot Test, Testcontainers 2.0.5. Mockito not used | R1, R2 | **PARTIAL:** JUnit major version differs from TC-5 (unreviewed deviation). Mockito unused (no service-unit tests with mocks) |

TC-5 is a technology constraint, not one of the 15 acceptance criteria, so it doesn't change the verdict. It needs an engineer decision.

---

## 4. Conditions (open, not acceptance criteria)

| # | Item | Source | Needed |
|---|------|--------|--------|
| C-1 | **Milestone 02:** requirements analysis completed after this review (`spec/requirements.md` §2–§17: actors, NFRs, business/validation/error rules, assumptions register, 11 open questions, 6 decisions). The assumptions still need sign-off (D-6), and the functional spec (SDD phase 3) was never written separately | Engineer's note, spec review SR-01 | Sign-off of `spec/requirements.md` §15–§17 |
| C-2 | Phase 0 spec decisions open: contract semantics (SR-02/03), `openapi.yaml`, pinned-version ADR | Spec review, backend report O-1…O-5 | STEP-01…05 |
| C-3 | **No authentication by design**, and no recorded deployment boundary | Security review H-2 | ADR before any deployment |
| C-4 | Frontend security headers missing, `X-Powered-By` exposed | Security review M-2 | Hardening |
| C-5 | No CI pipeline yet. Everything above was run locally | Code review M-7 | Plan STEP-11/49/62 |
| C-6 | JUnit Jupiter 6 vs TC-5 "JUnit 5" | This review | Engineer decision |
| C-7 | Minor code-review items remain (e.g. unplaced field errors lack a field name, UI duplicates the terminal-state rule) | Code review m-2, m-4, … | Backlog |
