# Final Acceptance Review — 2026-09-26

| Baseline | Source of truth | Code changed during review |
|----------|-----------------|----------------------------|
| Commit `0aee402` (working tree: docs only) | `spec/requirements.md` (REQ-1…12, TC-1…5) and the 15 acceptance criteria given for this review | No |

## Verdict: **NOT ACCEPTED**

| PASS | PARTIAL | FAIL | NOT VERIFIED |
|------|---------|------|--------------|
| 1 | 1 | 13 | 0 |

**Root cause:** the Spring Boot backend implements none of the ticket API. It contains only its entry-point class, so
every `/api/v1/tickets…` request returns Spring's default `404`. The frontend and the test suites exist and behave
correctly against a contract stub, but the stub is a test tool, not the product. **Acceptance is judged against the
real system only.**

---

## 1. Test runs performed for this review

| # | Suite | Command | Result |
|---|-------|---------|--------|
| R1 | Backend unit + integration | `backend/ ./gradlew clean build` | ✅ BUILD SUCCESSFUL — 2 tests (application annotation, context load) |
| R2 | Backend spec-derived API tests | `backend/ ./gradlew specAcceptanceTest` | ❌ **240 of 241 failed.** Every failure is the default `404`. The only pass is a self-check of the transition table's row count |
| R3 | Frontend unit/component tests | `frontend/ npx vitest run` | ✅ 73/73 (6 files). `typecheck` ✅, `lint` ✅, production `build` ✅ |
| R4 | E2E, real stack (Chromium → Next.js prod build → proxy → backend jar) | `e2e/ npx playwright test` | ❌ **19 of 21 failed**, all with `404` from the backend. Passed: J11 client-side validation, J12 backend-unavailable message |
| R5 | E2E vs contract stub (reference only, *not* acceptance evidence) | `e2e/ E2E_BACKEND=stub npx playwright test` | 20/21. The one failure is frontend defect I-2 (stale conflict message) |
| R6 | Secret scan | `git grep` patterns over tracked files, `git log -p --all`, tracked `.env`/`.pem`/`.key` count | ✅ 0 / 0 / 0 |
| R7 | Environment | `docker info` | ❌ Docker daemon not running. PostgreSQL and Testcontainers unavailable |

R2 results per class:

| Class | Tests | Failed |
|-------|-------|--------|
| `TicketCreationApiIT` | 12 | 12 |
| `TicketListingApiIT` | 10 | 10 |
| `TicketRetrievalApiIT` | 4 | 4 |
| `TicketUpdateApiIT` | 10 | 10 |
| `AssigneeApiIT` | 12 | 12 |
| `CommentApiIT` | 13 | 13 |
| `TicketSearchApiIT` | 18 | 18 |
| `StatusFilterApiIT` | 15 | 15 |
| `BackendValidationApiIT` | 80 | 80 |
| `NotFoundApiIT` | 7 | 7 |
| `ErrorContractApiIT` | 8 | 8 |
| `StatusTransitionApiIT` | 52 | 51 |

---

## 2. Acceptance criteria

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| AC-1 | Ticket can be created from UI | **FAIL** | R4 J1: after submit the URL stays `/tickets/new`, because `POST /api/v1/tickets` → `404`. R2 `TicketCreationApiIT` 0/12. (R3 `CreateTicketForm.test.tsx` 6/6 and R5 J1 pass: the UI is ready, the backend isn't) |
| AC-2 | Tickets can be listed | **FAIL** | R4 J2: test setup `create ticket` → `404`. R2 `TicketListingApiIT` 0/10 |
| AC-3 | Ticket details can be viewed | **FAIL** | R4 J3 → `404`. R2 `TicketRetrievalApiIT` 0/4 |
| AC-4 | Ticket fields can be updated | **FAIL** | R4 J4 → `404`. R2 `TicketUpdateApiIT` 0/10. Also known defect **M-1**: the edit form can revert concurrent changes after a reload (code review) |
| AC-5 | Assignee can be changed | **FAIL** | R4 J5 → `404`. R2 `AssigneeApiIT` 0/12 |
| AC-6 | Comments can be added | **FAIL** | R4 J6 → `404`. R2 `CommentApiIT` 0/13 |
| AC-7 | Search works | **FAIL** | R4 J7 ×2 → `404`. R2 `TicketSearchApiIT` 0/18. Also known defect **M-2**: the search box re-applies an old keyword after navigation |
| AC-8 | Status filter works | **FAIL** | R4 J8 → `404`. R2 `StatusFilterApiIT` 0/15 |
| AC-9 | Valid status transitions work | **FAIL** | R4 J9 ×2 → `404`. R2 `StatusTransitionApiIT` allowed A1–A5: 0/5 |
| AC-10 | Invalid status transitions are rejected by backend | **FAIL** | The backend returns `404` for every request, including transitions. It rejects everything because the endpoint doesn't exist, **not** because of the state machine. R4 J10 direct API (`CLOSED/RESOLVED/CANCELLED → OPEN`, expects `409 TICKET_INVALID_TRANSITION`) fails at setup. R2 rejected R1–R8: 0/8. A 404 must not be counted as a rejection |
| AC-11 | Data survives application restart | **FAIL** | No persistence layer exists (no datasource, Flyway or entities). R4 J13 → `404`. Test-strategy TS-PERS-01…06 are not implemented. Even with the backend built, verification needs PostgreSQL, which is unavailable (R7) |
| AC-12 | Backend validation works | **FAIL** | R2 `BackendValidationApiIT` 0/80. R4 J11 API case: expected `400 VALIDATION_FAILED`, got `404` |
| AC-13 | UI shows meaningful errors | **PARTIAL** | **Works:** client-side hints (R4 J11 client ✅). Backend unreachable → "Something went wrong… Try again" (R4 J12 ✅). All contract error codes map to messages (R3 `errors.test.ts`, form and details tests ✅). **Doesn't work end to end:** backend-originated errors can't be shown meaningfully, because the backend never sends contract errors. An unknown ticket shows "The server returned an unexpected response" instead of "Ticket not found" (R4 J12 not-found ❌). Server validation is shown the same generic way (R4 J11 UI ❌). Known defect I-2: the conflict message isn't cleared after reload (R5) |
| AC-14 | State-machine integration tests pass | **FAIL** | R2 `StatusTransitionApiIT` 1/52 (the pass is only a row-count self-check). R4 J9/J10 0/6 |
| AC-15 | No secrets are committed | **PASS** | R6: 0 matches in 141 tracked files, 0 in the full history, no tracked `.env`/`.pem`/`.key`. Security review 2026-09-26 found no secrets (its separate High findings, Tomcat CVEs and no-auth, aren't secret exposure) |

No criterion is **NOT VERIFIED**. Each was checked against the running system, and the failures are observed, not
assumed. AC-11 is FAIL rather than NOT VERIFIED because the missing persistence layer is itself verified. Separately,
the environment (R7) would block verification even once it exists.

---

## 3. Traceability matrix

A functional specification (`spec/functional-spec.md`, SDD phase 3) was never written. The "Specification" column
therefore cites the design documents that define the behaviour.

| Requirement | Specification section | Implementation | Automated tests | Current result |
|-------------|-----------------------|----------------|-----------------|----------------|
| **REQ-1** Create a ticket | `api-contract.md` §6.1, §4.1. `data-model.md` §3. `architecture.md` §7 | **Backend: none.** Frontend: `CreateTicketForm.tsx`, `features/tickets/api.ts`, `hooks/queries.ts` | `TicketCreationApiIT` (12). `BackendValidationApiIT$CreateTicket` (26). `CreateTicketForm.test.tsx` (6). E2E J1 | **FAIL:** API 0/12. FE 6/6. E2E ❌ |
| **REQ-2** List tickets | `api-contract.md` §6.2, §4.2, §4.4. `data-model.md` §9 | Backend: none. Frontend: `TicketListView.tsx`, `useTicketListParams.ts` | `TicketListingApiIT` (10). `TicketListView.test.tsx` (7 of 10). E2E J2 | **FAIL:** API 0/10. FE ✅. E2E ❌ |
| **REQ-3** View ticket details | `api-contract.md` §6.3, §6.3a | Backend: none. Frontend: `TicketDetailsView.tsx`, `app/tickets/[id]/page.tsx` | `TicketRetrievalApiIT` (4). `NotFoundApiIT` (7). `TicketDetailsView.test.tsx` (details, 3). E2E J3 | **FAIL:** API 0/4. FE ✅. E2E ❌ |
| **REQ-4** Update title, description, priority, assignee | `api-contract.md` §6.4, §6.5, §1.2 | Backend: none. Frontend: `EditTicketForm.tsx` (defect M-1), `AssigneeControl.tsx` | `TicketUpdateApiIT` (10). `AssigneeApiIT` (12). FE edit (6) and assignee (3) tests. E2E J4, J5 | **FAIL:** API 0/22. FE ✅ (but misses M-1). E2E ❌ |
| **REQ-5** Add comments | `api-contract.md` §6.6, §6.3a. `data-model.md` §4 | Backend: none. Frontend: `CommentsSection.tsx` | `CommentApiIT` (13). FE comment tests (6). E2E J6 | **FAIL:** API 0/13. FE ✅. E2E ❌ |
| **REQ-6** Search by keyword | `api-contract.md` §6.2.1. `data-model.md` §13 | Backend: none. Frontend: `TicketListView.tsx` (defect M-2), `ticketListQuery()` | `TicketSearchApiIT` (18). `api.test.ts` (5). FE search tests (2). E2E J7 ×2 | **FAIL:** API 0/18. FE ✅. E2E ❌ |
| **REQ-7** Filter by status | `api-contract.md` §6.2.2 | Backend: none. Frontend: `TicketListView.tsx` status checkboxes | `StatusFilterApiIT` (15). FE filter test (1). E2E J8 | **FAIL:** API 0/15. FE ✅. E2E ❌ |
| **REQ-8** Persist data | `data-model.md` §1–§12. ADR-0001 | **None:** no datasource, Flyway, entities or repositories | TS-PERS-01…06 **not implemented**. E2E J13 | **FAIL:** E2E ❌. Docker unavailable |
| **REQ-9** Validate input at the backend | `api-contract.md` §2.1–§2.3, §1.3 | **None** (frontend has client-side hints only, which don't satisfy REQ-9) | `BackendValidationApiIT` (80). E2E J11 API | **FAIL:** API 0/80. E2E ❌ |
| **REQ-10** Meaningful errors in the UI | `architecture.md` §6.2. `api-contract.md` §2, §8.1 | Frontend: `lib/api/errors.ts`, `client.ts`, `formErrors.ts`, `ErrorAlert.tsx` | `errors.test.ts` (20), `client.test.ts` (5), FE form and details error tests. `ErrorContractApiIT` (8). E2E J11 client/UI, J12 ×3 | **PARTIAL:** FE ✅. E2E 2/5 (J11 client ✅, J12 down ✅, J11 UI ❌, J12 not-found ❌, J12 recovery ❌). API 0/8 |
| **REQ-11** Allowed transitions | `state-machine.md` §3, §4. `api-contract.md` §3.1, §6.9 | Backend: none. Frontend: `StatusActions.tsx` (buttons from `allowedTransitions`) | `StatusTransitionApiIT` Allowed A1–A5, matrix M3 (5 allowed rows), Paths. FE status tests (9). E2E J9 ×2 | **FAIL:** API 0/5 (+0/5 matrix rows). FE ✅. E2E ❌ |
| **REQ-12** Reject invalid transitions | `state-machine.md` §4, §5, §8. `api-contract.md` §6.9 | **Backend: none** (no domain, no `TicketStatus`) | `StatusTransitionApiIT` Rejected R1–R8, matrix (20 rows), Enforcement (7), Versioning (4). E2E J10 ×3 | **FAIL:** API 0/39. E2E ❌ |
| **TC-1** Java 21, Spring Boot, REST | `architecture.md` §3 | `backend/build.gradle.kts` (toolchain 21, Boot 4.1.1) | R1 build | **PARTIAL:** stack in place, no REST endpoints |
| **TC-2** PostgreSQL (prod), H2 (tests/local) | `data-model.md` §12–§14. ADR-0001 | None | None | **FAIL:** not integrated |
| **TC-3** React / Next.js | `architecture.md` §6 | `frontend/` (Next 16.3.6, React 19.3) | R3 | **PASS** |
| **TC-4** Gradle | `rules/java-springboot.md` §8 | Gradle 9.8.0 wrapper, checksum pinned | R1 | **PASS** |
| **TC-5** JUnit 5, Spring Boot Test, Mockito, Testcontainers | `rules/testing.md` §2 | JUnit **Jupiter 6.0.3** (Boot BOM), Spring Boot Test | R1, R2 | **PARTIAL:** Jupiter 6 rather than 5 (unreviewed deviation). Mockito and Testcontainers unused |

---

## 4. What must happen before acceptance can be re-run

1. **Implement the backend** (implementation plan STEP-08…39): persistence, domain and state machine, services,
   controllers, validation, Problem Details. Then remove the `spec-acceptance` tags as each area passes. This addresses
   AC-1…12 and AC-14.
2. **Start Docker** (R7) and implement TS-PERS-01…06 on Testcontainers PostgreSQL. This addresses AC-11.
3. **Fix the frontend defects found in review:** M-1 (lost update), M-2 (search/URL), I-2 (stale conflict message),
   M-3 (build-time backend URL). Each needs a regression test that fails first. This addresses AC-4, AC-7 and AC-13.
4. **Resolve Phase 0** (functional spec, contract semantics SR-02/03, `openapi.yaml`) so the tagged tests assert
   approved behaviour. Record the JUnit 6 deviation from TC-5.
5. **Security High items** from the security review (Tomcat CVE pin, deployment boundary) before any release.

Re-run R1–R7. Acceptance requires **R2 and R4 green against the real backend with PostgreSQL**, not the stub.
