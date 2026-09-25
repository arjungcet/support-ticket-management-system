# Code Review — full implementation — 2026-09-26

| Scope | Against | Verdict |
|-------|---------|---------|
| `backend/`, `frontend/`, `e2e/` at commit `0aee402` + working tree | `spec/*.md`, `rules/java-springboot.md`, `rules/testing.md`, `rules/api-standards.md` | **Request changes.** 2 blockers, 9 major, 17 minor |

Severity:
- **blocker:** the system can't meet its requirements.
- **major:** a real defect, data-loss risk, or a gap that will let defects ship.
- **minor:** should fix.
- **nit:** optional.

Findings marked *confirmed* were verified by tracing the code path. None required running new code. No code was changed.

## Categories with nothing to report, and why

- **Secrets (17):** none. A scan of `backend/`, `frontend/`, `e2e/` found only fixture text ("…after password reset"). `.env` files are git-ignored, and only `.env.example` files exist.
- **Transactions (7), database (8), races in persistence (9), N+1 (11), indexes (12), backend state machine (2), backend
  status codes (4):** **there is no backend code to assess.** `backend/src/main/java` contains only
  `SupportDeskApplication`. These categories are therefore covered by **B-1**, not reported as "clean".
- **SQL injection:** no SQL exists.

---

## Blockers

### B-1 — The backend implements none of the requirements
- **File:** `backend/src/main/java/com/supportdesk/` (only `SupportDeskApplication.java`), `backend/src/main/resources/application.yml` (only `spring.application.name`)
- **Problem:**
  - REQ-1…REQ-12 are unimplemented: no entities, migrations, repositories, services, controllers, validation, state machine, error handler or correlation filter.
  - The configuration required by `rules/java-springboot.md` §5–6 and `architecture.md` §17 is absent: datasource, Flyway, `ddl-auto=validate`, `open-in-view=false`, profiles, actuator exposure.
- **Why:** every API call returns Spring's default `404` (confirmed: 240/241 spec-acceptance ITs and 19/21 E2E tests fail). The frontend can't function against it.
- **Fix:** implement plan STEP-08…STEP-39 in order, each with its own tests. Remove the `spec-acceptance` tag from each IT class as its feature lands.

### B-2 — Implementation is proceeding on specifications with open blockers
- **Files:** `spec/*.md` (review SR-01…SR-04 unresolved). `frontend/src/lib/api/types.ts`. `backend/src/integrationTest/**` (cases marked "review SR-02/SR-03"). `e2e/stub/contract-stub.mjs`.
- **Problem:**
  - Phase 0 (STEP-01…05) was skipped. STEP-07, STEP-46…58 and STEP-61 were built out of order.
  - The frontend, the backend ITs and the E2E stub each encode the contract's disputed semantics independently: null handling, error aggregation, 409 vs 422.
  - `openapi.yaml` doesn't exist.
- **Why:** when Phase 0 resolves SR-02/03/14, three hand-written copies of the contract must change in step. This is the rework SDD is meant to prevent (`rules/ai-assisted-development.md` §1).
- **Fix:** do STEP-01…05 now, before any further feature code. Generate frontend types from `openapi.yaml`, and update the tagged ITs in the same PR.

---

## Major

### M-1 — Editing after a reload silently reverts other users' changes (lost update) — *confirmed*
- **File:** `frontend/src/features/tickets/components/EditTicketForm.tsx` (form defaults from the first render; lines 49–53 compare **every** field with the current ticket)
- **Problem:**
  - `useForm` keeps the `defaultValues` from the first render. On submit, the body includes every field whose form value differs from the **latest** `ticket` prop, whether or not the user touched it.
  - Scenario:
    1. User A opens the ticket.
    2. User B changes the description.
    3. A edits the title and gets a `409`.
    4. A clicks Reload. The ticket prop now holds B's description, but A's form still holds the old one.
    5. A saves. The body contains `title` **and the old `description`**, with the new `version`, so B's change is overwritten.
  - The same happens after any background refetch that brings in other users' edits.
- **Why:** it defeats optimistic locking, which exists precisely to prevent lost updates (`state-machine.md` §9, `api-contract.md` §1.2). Silent data loss.
- **Fix:**
  - Send only fields in `formState.dirtyFields`.
  - When the `ticket` prop changes, update the defaults of untouched fields, e.g. `reset(valuesOf(ticket), { keepDirtyValues: true })` in an effect keyed on `ticket.version`.
  - Add a test covering reload-then-save that asserts the request body.

### M-2 — The search box fights URL navigation (Back button and "Tickets" link can't clear a search) — *confirmed*
- **File:** `frontend/src/features/tickets/components/TicketListView.tsx:29–37`
- **Problem:** `keyword` is initialised once from `params.q` and never updated from it. After the URL changes externally (Back button, header "Tickets" link to `/tickets`), `params.q` becomes `""` while `keyword` stays `"abc"`. The debounce effect sees a difference and calls `onParamsChange({ q: "abc" })`, putting the old search back.
- **Why:** it breaks browser navigation and the URL-as-source-of-truth principle (`architecture.md` §6.2). Users can't leave a search by navigating.
- **Fix:** sync local state when `params.q` changes externally (effect on `params.q` that sets `keyword`, guarded against the component's own updates), or keep the input uncontrolled and keyed by `params.q`. Add a test that re-renders with a different `params.q`.

### M-3 — Backend URL is fixed at build time, with a hard-coded localhost fallback
- **File:** `frontend/next.config.ts`
- **Problem:** `rewrites()` reads `process.env.BACKEND_URL ?? "http://localhost:8080"` during `next build`. The value is baked into `.next/routes-manifest.json`, and `BACKEND_URL` at `next start` is ignored (confirmed in the E2E run, finding I-3). If the variable is missing at build time, production silently proxies to `localhost:8080`.
- **Why:** it contradicts `architecture.md` §17 and `frontend/.env.example`. One artifact can't be promoted between environments, and a misconfiguration fails silently instead of at startup.
- **Fix:** proxy through a runtime Route Handler (`app/api/[...path]/route.ts`) that reads `BACKEND_URL` per request and fails fast if it's unset. Alternatively, document build-per-environment and fail the build when `BACKEND_URL` is missing. Remove the fallback outside development.

### M-4 — A green `./gradlew build` hides 240 failing spec tests
- **File:** `backend/build.gradle.kts` (`integrationTest` excludes tag `spec-acceptance`. The `specAcceptanceTest` task is not wired into `check` or CI)
- **Problem:** the only signal that the backend is missing its features lives in a task nobody is forced to run.
- **Why:** "build passes" is the plan's definition of done. It is currently meaningless for backend behaviour, and tagged classes can be forgotten after features land.
- **Fix:** run `specAcceptanceTest` in CI as a reported (non-blocking) job with a failure-count trend, or fail the build when a tagged class starts passing (so the tag must be removed). Track the untagging per plan step.

### M-5 — Required test layers are missing (unit, slice, repository, architecture)
- **Files:** `backend/src/test/**` (one trivial test), no ArchUnit, no `@WebMvcTest`, no `@DataJpaTest`.
- **Problem:** `rules/testing.md` §2–3 require a pyramid: domain unit tests including the exhaustive state-machine table, web-slice tests of the error contract, and repository tests on PostgreSQL. Only black-box HTTP tests exist.
- **Why:** when the backend is built, failures will only surface as slow, coarse HTTP failures. Layering violations won't be caught at all (STEP-10 not done).
- **Fix:** add ArchUnit (STEP-10) before any feature code. Deliver each step's unit, slice and repository tests with the step.

### M-6 — The frontend tests passed while M-1 and M-2 exist
- **Files:** `frontend/src/features/tickets/components/TicketDetailsView.test.tsx` ("keeps the user's input…on a version conflict"), `TicketListView.test.tsx`
- **Problem:**
  - The conflict test asserts that the input is kept, but not what is **sent** after reloading, which is exactly where M-1 lives.
  - No test re-renders `TicketListView` with an externally changed `params.q` (M-2).
  - Not tested at all: pages (`app/**`) and `useTicketListParams` with a real router; `providers.shouldRetry`; comment pagination; `AssigneeControl`'s client-side length check; `ErrorAlert` retry.
- **Why:** 73 green tests give false confidence on the two riskiest interactions: concurrent editing and URL state.
- **Fix:** add the two regression tests above first; they should fail. Then cover the listed gaps.

### M-7 — No coverage gate and no CI
- **Files:** `backend/build.gradle.kts` (no `jacocoTestCoverageVerification`), no `.github/workflows/*`, no frontend coverage config.
- **Problem:** `rules/testing.md` §3/§7 require ≥ 80 % line and ≥ 70 % branch on `domain`/`application`, frontend thresholds, and CI running lint, typecheck and test.
- **Why:** nothing enforces the rules. Regressions depend on someone remembering to run four commands in three folders.
- **Fix:** plan STEP-11, STEP-45, STEP-49, STEP-62.

### M-8 — Three hand-maintained copies of the API contract
- **Files:** `frontend/src/lib/api/types.ts` + `frontend/src/test/fixtures.ts`, `backend/src/integrationTest/**` (JSON shapes and codes), `e2e/stub/contract-stub.mjs` (re-implements the whole contract, including validation and the state machine)
- **Problem:** there's no machine-readable contract (`spec/openapi.yaml`, SR-04), so each layer transcribes `api-contract.md` by hand. The stub also has a known deviation: it validates after the 404 lookup, which breaks the precedence in `api-contract.md` §1.3.
- **Why:** drift is inevitable. Frontend tests can pass against fixtures the backend will never produce, and the stub duplicates business rules outside the domain (`architecture.md` §8.3).
- **Fix:**
  - Write `openapi.yaml`, then generate frontend types (STEP-47) and validate IT responses against it (STEP-43).
  - Type MSW fixtures from the generated schema.
  - Delete the contract stub once the backend passes the E2E suite, or keep it only as a documented consumer-contract tool validated against `openapi.yaml`.

### M-9 — The E2E harness can silently test the wrong server
- **File:** `e2e/scripts/servers.ts` (`startBackend`, `waitForHttp`, `BACKEND_PORT = 8080`)
- **Problem:**
  - The backend port is hard-coded to 8080 (a workaround for M-3), which is also the default port of any developer's running backend.
  - `startBackend` doesn't check that the port is free, and `waitForHttp` accepts **any** HTTP response. If a dev backend is already on 8080, the spawned jar fails to bind and dies, and the tests run against the dev server.
  - `stopBackend` sends only SIGTERM and never escalates.
- **Why:** false passes or false failures depending on what else is running. Results aren't trustworthy.
- **Fix:** fail setup if the port is in use before spawning. Wait for a readiness endpoint that identifies the process (e.g. `/actuator/info` build info, or a stub-specific header). Check the spawned child is still alive. Escalate to SIGKILL after a timeout. Make ports configurable once M-3 is fixed.

---

## Minor

| ID | File | Problem | Why | Fix |
|----|------|---------|-----|-----|
| m-1 | `frontend/.../StatusActions.tsx`, `EditTicketForm.tsx`, `AssigneeControl.tsx` | The conflict alert ("changed by someone else — Reload") stays after the ticket has been reloaded (E2E finding I-2) | Tells the user to do something already done. Stale guidance | Clear `failure` when `ticket.version` changes, or after a successful reload |
| m-2 | `frontend/.../labels.ts` (`TERMINAL_STATUSES`), `TicketDetailsView.tsx`, `CommentsSection.tsx` | The UI hard-codes "CLOSED/CANCELLED can't be edited or commented on" | Business rule duplicated outside the domain. It drifts if A-17/A-18 change | Contract change: add `editable`/`commentable` to `TicketResponse` (spec first), then derive the UI from them |
| m-3 | `frontend/src/lib/api/client.ts` | No `AbortSignal` (TanStack's `signal` is ignored) and no request timeout | Requests continue after unmount or parameter change. A hung backend leaves spinners forever | Accept `signal` in `apiRequest` and pass it from `queryFn`. Add a timeout with `AbortSignal.timeout` |
| m-4 | `frontend/src/components/ui/ErrorAlert.tsx:19`, `lib/api/formErrors.ts` | Unplaced field errors are shown without the field name ("This field is required." for `version`). The message text is used as the React `key`, so duplicates collide | Not "meaningful" (REQ-10). React key warnings, and duplicate messages dropped | Prefix unplaced messages with a field label. Key by index or by `field:code` |
| m-5 | `frontend/.../TicketDetailsView.tsx` | A failed refetch with a cached ticket (non-404) is silently ignored, and stale data is shown with no indication | The user acts on stale data | Show a non-blocking alert when `error && ticket` |
| m-6 | `frontend/.../TicketDetailsView.tsx` (`key` on `AssigneeControl`) | Remounting on an assignee change discards the user's typed input after a conflict reload, unlike the edit form | Inconsistent conflict behaviour between controls | Keep input and update the baseline (same pattern as the M-1 fix) |
| m-7 | `frontend/next.config.ts`, `frontend/src/app/error.tsx` | `X-Powered-By: Next.js` is sent. No security headers (CSP, `X-Content-Type-Options`, `Referrer-Policy`). `error.tsx` discards the error without logging | Minor information leak and missing hardening (`rules/security.md` §4). Unexpected UI errors leave no trace | `poweredByHeader: false`, a `headers()` block with a baseline policy, `console.error`/reporting in `error.tsx` |
| m-8 | `frontend/src/lib/api/types.ts`, `features/tickets/labels.ts` | Dead code: `TICKET_SORT_FIELDS`, `TicketSortField` and `SortDirection` are unused. `TRANSITION_LABELS.OPEN = "Reopen"` is unreachable (A-20 forbids reopen) | Misleading. "Reopen" suggests the feature exists | Remove, or use the sort constants in `TicketListView`'s `SORT_OPTIONS`. Drop the `OPEN` label |
| m-9 | `e2e/scripts/servers.ts` | Platform- and version-specific hard-coding: `/usr/libexec/java_home` (macOS only), jar name `support-desk-backend-0.0.1-SNAPSHOT.jar`, fixed ports | Breaks on Linux CI and on any version bump | Read `JAVA21_HOME`/`JAVA_HOME`, glob the boot jar (or pass it via env), make ports configurable |
| m-10 | `e2e/tests/persistence.spec.ts` + `e2e/stub/contract-stub.mjs` | In stub mode J13 passes because the stub writes a JSON file. It proves nothing about the real persistence (REQ-8) | A green J13 in stub runs can be misread as evidence | Skip J13 in stub mode, or mark it clearly in the report |
| m-11 | `backend/build.gradle.kts:90` | `executionData(tasks.test.get(), integrationTest.get())` realises tasks eagerly | Defeats Gradle configuration avoidance and is incompatible with the configuration cache later | `executionData(tasks.test, integrationTest)` (providers) |
| m-12 | `backend/src/integrationTest/.../support/ApiClient.java` (`parse`) | Catches every `RuntimeException` and returns `null` | Hides unexpected parse failures. Assertion messages become "expected object but got …" far from the cause | Catch only Jackson's parse exception |
| m-13 | `backend/src/integrationTest/.../StatusTransitionApiIT.java` (`concurrentIdenticalTransitions`) | `CompletableFuture.supplyAsync` on the common pool, `join()` without timeout. The outcome is identical whether or not the requests overlap (review SR-09) | Can hang the suite, and doesn't prove the race handling it names | Use `orTimeout`. Replace with the deterministic interleaving hook (STEP-41) |
| m-14 | `backend/src/test/java/com/supportdesk/SupportDeskApplicationTest.java` | Asserts an annotation's presence (an implementation detail) | Low value. `rules/testing.md` §1 asks for behaviour | Delete it once real unit tests exist |
| m-15 | `AGENTS.md`, `spec/implementation-plan.md` | Status says "phase 9, no application code". The plan doesn't record which steps are done or the deviations (versions chosen without STEP-04, frontend built before the backend) | Agents and humans get a false picture of the project state | Add a progress table to the plan. Update the `AGENTS.md` status on each step |
| m-16 | `frontend/.../CommentsSection.tsx` | `readRememberedAuthor()` is evaluated on every render (argument to `useForm`) | Needless `localStorage` reads | Lazy `useState(() => readRememberedAuthor())` and pass it in |
| m-17 | `frontend/.../TicketListView.tsx` (`maxLength` on search) | The limit applies to untrimmed UTF-16 length, while the server limit is after trimming | Nit: leading or trailing spaces count against the user | Validate the trimmed length instead of relying on `maxLength` |

---

## Summary by requested category

| # | Category | Findings |
|---|----------|----------|
| 1 | Incorrect business logic | M-1 (lost update), B-1 |
| 2 | State-machine violations | B-1 (backend absent). m-2 (UI rule duplication). Frontend transitions correctly come from `allowedTransitions` |
| 3 | Missing validation | B-1 (no backend validation). m-17 |
| 4 | Incorrect HTTP status codes | B-1 (every route 404) |
| 5 | API contract violations | B-1, B-2, M-8 |
| 6 | Security | m-7. Unauthenticated by design (A-4, see spec review SR-12) |
| 7 | Transaction problems | none assessable (B-1) |
| 8 | Database issues | none assessable (B-1) |
| 9 | Race conditions | M-1 (client-side lost update). m-13 |
| 10 | Poor exception handling | m-5, m-7 (`error.tsx`), m-12 |
| 11 | N+1 queries | none assessable (B-1) |
| 12 | Missing indexes | none assessable (B-1) |
| 13 | Weak test coverage | M-4, M-5, M-6, M-7, m-10, m-14 |
| 14 | Over-mocking | None found. Frontend tests mock at the network layer (MSW), backend tests are black-box. Residual risk: fixtures are hand-written (M-8) |
| 15 | Frontend/backend mismatches | B-1, M-3, M-8, m-2 |
| 16 | Hard-coded configuration | M-3, M-9, m-9 |
| 17 | Secrets | None |
| 18 | Dead code | m-8 |
| 19 | Unnecessary complexity | M-8 (contract stub re-implementing the backend). `specAcceptanceTest` split (M-4) |
| 20 | Maintainability | B-2, M-8, m-15 |

## Recommended order

1. B-2: Phase 0 (spec decisions, `openapi.yaml`, pinned versions).
2. M-1, M-2 with their regression tests (M-6). These are user-facing defects in code that exists today.
3. M-3, M-9 (configuration), then M-7 (CI) and M-4.
4. B-1 via plan STEP-08…39, delivering M-5's test layers with each step.
5. Minor items opportunistically, each in its own small PR.
