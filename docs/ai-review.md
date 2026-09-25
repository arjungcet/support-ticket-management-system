# AI-Output Audit

| Date | Scope | Method |
|------|-------|--------|
| 2026-09-26 | Every AI-produced artifact in this repository: rules, specs, implementation plan, backend skeleton and tests, frontend, E2E suite, and the AI's own review reports | Re-reading the prompt history (`docs/prompt-history.md`), the review reports in `docs/reviews/`, build and test output, plus one new executable check (§3) |

This document records mistakes, incorrect suggestions and risky assumptions **made by the AI assistant** during
development. Each one is shown with the evidence that exposed it. Nothing here was invented to fill a quota. Items the
audit considered and rejected are listed in §11.

**How AI output was reviewed.** The engineer did not accept generated artifacts as final. At each phase gate they
ordered an adversarial pass: a skeptical spec review, an integration/E2E run with failure classification, a strict code
review, and this audit. They also required every failure to be classified rather than hidden. Most issues below were
caught by those gates, not by the step that produced them. Where an issue is **not yet fixed**, this document says so.

## Summary

| # | Issue | Area | Detected by | Status |
|---|-------|------|-------------|--------|
| AI-1 | Build mixed two JUnit major versions, and the IT suite couldn't see app dependencies | Build (STEP-07) | Dependency-graph inspection | **Fixed** |
| AI-2 | Redirect HTML written into the Gradle wrapper checksum | Build (STEP-07) | Reading command output | **Fixed** |
| AI-3 | API contract promised framework behaviour that Jackson 3 doesn't have | Spec / contract | Spec review, then an executable check | **Open** (Phase 0 STEP-02) |
| AI-4 | Contract claimed "no tri-state handling needed" while requiring it | Spec / contract | Spec review | **Open** (STEP-02) |
| AI-5 | Test strategy specified search tests that could never fail | Test design | Spec review | **Partly fixed** (tests fixed, spec not yet) |
| AI-6 | Backend address baked in at frontend build time; "integration verified" was a false positive | Frontend config | E2E run on a non-default port | **Fixed** 2026-09-26 (runtime proxy) |
| AI-7 | Edit form can overwrite other users' changes; the generated test asserted the wrong thing | Frontend logic + test | Code review (path trace) | **Fixed** 2026-09-26 |
| AI-8 | E2E tests written with three defects and not run before being trusted | E2E tests | Running against the contract stub | **Fixed** |
| AI-9 | Concurrency tests that pass whether or not the race is handled | Test design | Spec review | **Open** (STEP-41 hook) |

---

## AI-1 — Build mixed JUnit 5 and JUnit 6, and integration tests couldn't see application dependencies

1. **AI output.** In STEP-07 the AI configured both Gradle test suites with `useJUnitJupiter()` and reported the build
   as complete, stating that "versions come only from the catalog or the Boot BOM".
2. **Why incorrect.**
   - `useJUnitJupiter()` without a version makes Gradle add its own default `junit-jupiter:5.12.2`. Spring Boot 4.1.1
     manages JUnit **6.0.3**. The result was a mixed classpath: an aggregator at 5.12.2 with API/engine jars forced to
     6.0.3. That contradicted the AI's own claim and `rules/java-springboot.md` §8.
   - Separately, the custom `integrationTest` suite only depended on `project()`, so application `implementation`
     dependencies (Spring Web) weren't on its compile classpath. This went unnoticed because the one existing IT didn't
     need them.
3. **How detected.** While writing the spec-acceptance tests, the AI inspected the resolved dependency graph before
   choosing test libraries. Compiling the new ITs then exposed the second problem.
4. **Evidence.**
   - `./gradlew dependencies --configuration integrationTestRuntimeClasspath` showed
     `org.junit.jupiter:junit-jupiter:6.0.3 -> 5.12.2` and `junit-jupiter-api:5.12.2 -> 6.0.3`.
   - `compileIntegrationTestJava FAILED … package org.springframework.http does not exist`.
5. **Correct decision.** Test framework versions must come from the Spring Boot BOM, and custom test suites must inherit
   the application's dependencies the way the built-in `test` suite does.
6. **Fix (done).** `backend/build.gradle.kts` now reads `dependencyManagement.importedProperties["junit-jupiter.version"]`
   and passes it to `useJUnitJupiter(...)`. `integrationTestImplementation`/`RuntimeOnly` extend `implementation`/
   `runtimeOnly`. The graph now resolves a single `junit-jupiter:6.0.3`, and all suites compile and run.
7. **Prevention.** Add to `rules/java-springboot.md` §8: *"After any build-script change, run
   `./gradlew dependencies` for each test configuration and confirm no managed library resolves to two versions
   (`a -> b` conflicts). Custom test suites must extend the main `implementation` configuration."* Add this as an item
   in `commands/review-code.md`.

## AI-2 — Redirect page written into the Gradle wrapper checksum

1. **AI output.** To pin the wrapper distribution, the AI ran
   `SUM=$(curl -s …/gradle-9.8.0-bin.zip.sha256)` and passed `$SUM` to `gradle wrapper --gradle-distribution-sha256-sum`.
2. **Why incorrect.** `services.gradle.org` answers that URL with a **301 redirect**, and `curl` without `-L` doesn't
   follow it. The "checksum" was an HTML page. The wrapper wrote it into `gradle-wrapper.properties` as
   `distributionSha256Sum=<html>…301 Moved Permanently…`. Any fresh checkout would then fail to download Gradle,
   during checksum verification.
3. **How detected.** The AI printed the resulting properties file as part of the same command and read it.
4. **Evidence.** The command output showed `sha=<html> <head><title>301 Moved Permanently</title>…` and the corrupted
   property line.
5. **Correct decision.** Never write unvalidated network output into build configuration. Follow redirects and validate
   the shape of the value.
6. **Fix (done).** Re-ran with `curl -sL`, guarded by `[[ "$SUM" =~ ^[0-9a-f]{64}$ ]]`. The file now holds
   `distributionSha256Sum=bafd5ce9…58e6c`, and `./gradlew` has run successfully many times since.
7. **Prevention.** Add to `rules/ai-assisted-development.md` §3: *"Values fetched from the network (checksums, versions,
   URLs) must be validated against an expected format before being written to any file, and the written file must be
   shown in the PR."*

## AI-3 — API contract promised framework behaviour that Jackson 3 doesn't have

1. **AI output.**
   - `spec/api-contract.md` §2.1: `"title": 123` and `"version": "3"` → `400 MALFORMED_REQUEST`.
   - §1.1: unknown properties → `400 VALIDATION_FAILED`/`UNKNOWN_FIELD`.
   - §1.3: *all* field errors, including unknown fields and invalid enum values, reported together.
   - `rules/api-standards.md` §4 names `FAIL_ON_UNKNOWN_PROPERTIES=true` as the mechanism.

   Separately, the AI chose Spring Boot 4.1.1 (which ships **Jackson 3**) without re-checking these statements.
2. **Why incorrect.**
   - With default settings, Jackson **accepts** both mismatched types (coercion).
   - Jackson 3's default for unknown properties is **accept**, not reject.
   - An invalid enum value aborts deserialisation before Bean Validation runs, so it can't be reported "together" with
     other field errors.
   - The contract therefore describes behaviour that a default Spring setup won't produce, and the backend ITs
     (TS-VAL-06/08/10/31) encode it as expected results.
3. **How detected.** The spec review flagged it by reasoning (SR-03). This audit then **verified it by execution**
   against the exact library version on the project's classpath (Jackson 3.1.5).
4. **Evidence.** Output of a standalone check compiled against `jackson-databind-3.1.5.jar`:
   ```
   Jackson default FAIL_ON_UNKNOWN_PROPERTIES = false
   version as string "3"                            ACCEPTED  version=3
   title as number 123                              ACCEPTED  title=123
   unknown property                                 ACCEPTED
   unknown enum + unknown property                  REJECTED  InvalidFormatException
   ```
   The last line shows only the first failure is reported: the unknown property never reaches validation.
5. **Correct decision.** Pick an implementable strategy and write it down:
   - disable scalar coercion
   - explicitly enable unknown-property failure (or collect unknown keys)
   - bind enums as strings validated by a constraint, so all errors can be aggregated

   Alternatively, change the contract to say deserialisation-level errors are reported alone. Either way, prove the
   choice with a spike before tests depend on it.
6. **Fix (not yet done).** Tracked as implementation plan **STEP-02** (contract semantics) and **STEP-04** (pinned
   versions, "re-check every API named in the specs"). The affected ITs carry a "(review SR-03)" marker so they are
   updated together with the decision.
7. **Prevention.** Add to `commands/review-spec.md`: *"Every statement about framework or library behaviour (defaults,
   coercion, error aggregation, ordering) must cite either documentation for the pinned version or a spike result.
   Unverified statements are marked `UNVERIFIED` and block implementation of the dependent steps."*

## AI-4 — Contract claimed "no tri-state handling needed" while requiring it

1. **AI output.** `spec/api-contract.md` §5 and `spec/architecture.md` §10: moving assignee to `PUT /assignee`
   *"resolves A-29 … No tri-state JSON handling is needed."* The same contract required:
   - PATCH: absent field = unchanged, but explicit `null` = `REQUIRED` (§6.4)
   - PUT assignee: the `assignee` key must be present, and `null` = unassign (§6.5, API-3)
2. **Why incorrect.** Both rules need to distinguish a *missing* property from an explicit `null`, which plain Java
   records with Bean Validation cannot do. The design removed the mechanism while keeping the requirement. An
   implementer would either quietly break the contract or re-add the mechanism.
3. **How detected.** The skeptical specification review (SR-02) cross-read §5 against §6.4/§6.5.
4. **Evidence.** The three quoted passages contradict each other. The ITs `explicitNull` and `keyMissing` in
   `BackendValidationApiIT` encode the unimplementable behaviour (marked "review SR-02").
5. **Correct decision.** Either treat `null` the same as absent (and a missing `assignee` key as `null`), or keep the
   strict rules and name the tri-state type in the architecture. The review recommends the former.
6. **Fix (not yet done).** Plan STEP-02. The frontend isn't affected: it never sends `null` for editable fields.
7. **Prevention.** Add to `skills/documentation/SKILL.md` §2: *"Each API rule that distinguishes inputs (absent vs null
   vs blank) must name the mechanism that implements it."* Add the check "absent vs null semantics per field" to
   `commands/review-spec.md`.

## AI-5 — Test strategy specified search tests that could never fail

1. **AI output.** `spec/test-strategy.md` §9 seed data included `"Discount 100% not applied"` and
   `"Promo code_2026 rejected"`. TS-SRCH-04/05 expect `100%` and `code_2026` to match only ticket 3, "`_` literal
   (would match `codeX2026` if unescaped)". No `codeX2026` row existed.
2. **Why incorrect.**
   - An unescaped pattern `%100%%` still matches only ticket 3.
   - An unescaped `code_2026` still matches only ticket 3.

   So the tests pass even if LIKE-escaping is completely broken. That's exactly the defect they exist to catch, and it
   would let SQL-wildcard bugs ship.
3. **How detected.** The skeptical spec review (SR-15), by evaluating each query against the seed data *without*
   escaping.
4. **Evidence.** Hand evaluation of the unescaped patterns over the §9 table. Every seeded row matches identically with
   and without escaping.
5. **Correct decision.** Every negative-path test needs a decoy that only a broken implementation would match. "A test
   that has never been seen failing is not trusted" (`rules/testing.md` §1).
6. **Fix (partly done).**
   - The generated `TicketSearchApiIT` seeds two decoys, `D1 "Order 1000 items"` and `D2 "Promo codeX2026 expired"`,
     and asserts they're excluded.
   - `spec/test-strategy.md` §9 hasn't been updated yet (plan STEP-03).
   - The tests can't be executed until the backend exists (AI-6 context).
7. **Prevention.** Add to `commands/generate-tests.md` step 4: *"For each negative or escaping case, name the decoy
   input that a wrong implementation would match, and include it in the fixture."*

## AI-6 — Backend address baked in at frontend build time; the "integration verified" claim was a false positive

1. **AI output.**
   - `frontend/next.config.ts` reads `process.env.BACKEND_URL ?? "http://localhost:8080"` inside `rewrites()`.
   - `frontend/.env.example` documents `BACKEND_URL` as a server-side runtime variable (matching
     `architecture.md` §17).
   - The frontend report stated: *"Browser → Next.js proxy → backend ✅ Requests reach Spring Boot."*
2. **Why incorrect.**
   - Next.js evaluates `rewrites()` at **build** time and writes the destination into `.next/routes-manifest.json`.
     `BACKEND_URL` at `next start` is ignored.
   - The AI's integration check passed only because the backend happened to run on the fallback port 8080. The check
     couldn't have failed, so it verified nothing about configurability.
3. **How detected.** The E2E harness started the backend on port 18080. Every proxied call failed.
4. **Evidence.**
   - `.state/frontend.log`: `Error: connect ECONNREFUSED 127.0.0.1:8080`.
   - `.next/routes-manifest.json`: `"destination": "http://localhost:8080/api/:path*"`.
   - 19 of 20 E2E tests failed against a correct stub until the port was aligned.
5. **Correct decision.** Proxy through a runtime Route Handler that reads `BACKEND_URL` per request and fails if it's
   unset. Configuration must be verified with a **non-default** value.
6. **Fix (done 2026-09-26).**
   - The rewrite was replaced by a runtime Route Handler, `frontend/src/app/api/[...path]/route.ts`. It reads
     `BACKEND_URL` on every request, answers `500` when it is unset outside `next dev`, forwards only API headers,
     and answers `502` when the backend is unreachable.
   - Regression tests: `route.test.ts` (5), including one that switches `BACKEND_URL` between two requests.
   - The E2E harness now runs the backend on the non-default port 18080, and the stub run passes 21/21.
7. **Prevention.** Add to `rules/testing.md` §7: *"Every environment variable the application reads must be exercised by
   at least one test or smoke check with a non-default value."* Add to `rules/ai-assisted-development.md` §3: *"An
   integration check only counts as verification if it could have failed."*

## AI-7 — Edit form can overwrite other users' changes, and the generated test asserted the wrong thing

1. **AI output.**
   - `frontend/.../EditTicketForm.tsx` builds the PATCH body by comparing **every** form value with the latest `ticket`
     prop, with form defaults fixed at first render.
   - The AI's test `keeps the user's input and offers a reload on a version conflict` asserts that the typed title
     survives a reload.
   - The AI reported "73/73 tests pass".
2. **Why incorrect.**
   - After a conflict and reload, untouched fields still hold the *old* values. On save, any field another user changed
     differs from the reloaded ticket, so the old value is sent with the *new* version. The colleague's change is
     silently reverted.
   - This defeats the optimistic locking designed in `state-machine.md` §9.
   - The test checked the UI state but never the **request body**, which is where the defect lives.
3. **How detected.** The strict code review (finding M-1) traced the submit path after a reload.
4. **Evidence.**
   - Trace: defaults come from the first render, and `values.description !== ticket.description` after reload adds
     `description` to the body.
   - The test file's assertions contain no check on the PATCH body after reload (M-6).
5. **Correct decision.** Send only user-modified fields (`formState.dirtyFields`), and update untouched fields' defaults
   when the ticket changes (`reset(…, { keepDirtyValues: true })`). Tests of conflict flows must assert what is sent.
6. **Fix (done 2026-09-26).**
   - `EditTicketForm` now sends only `dirtyFields`, and re-bases untouched fields on the reloaded ticket with
     `reset(..., { keepDirtyValues: true })`.
   - Regression test `after reloading a conflicted ticket, saving sends only the user's changes`. It failed before
     the fix: the body contained `"description": "Original description"`. It passes after.
7. **Prevention.** Add to `rules/testing.md` §6 (frontend): *"Tests for save/conflict flows must assert the outgoing
   request payload, not only the rendered state."* Add to `commands/review-code.md`: *"Look for lost updates: after any
   refetch, which fields would a save send?"*

## AI-8 — E2E tests written with three defects, and first failures couldn't be attributed

1. **AI output.** The first version of the E2E suite used:
   - `page.getByRole("alert")` for application errors
   - `locator.check()` on the status-filter checkboxes
   - a count of every button inside the "Status actions" region
2. **Why incorrect.**
   - Next.js renders a hidden route announcer with `role="alert"`, so the locator was ambiguous (strict-mode violation).
   - The checkboxes are controlled by the URL, so their state changes after navigation. `check()` requires an immediate
     change.
   - The region also contains the error alert's own "Reload ticket" button.

   Run against the real backend alone, all of these would have been indistinguishable from "backend missing" failures
   and blamed on the implementation.
3. **How detected.** The AI ran the suite against a contract stub (an in-memory implementation of `api-contract.md`)
   before trusting any failure against the real backend.
4. **Evidence.** Stub-run messages:
   - `strict mode violation: getByRole('alert')`
   - `Clicking the checkbox did not change its state`
   - `Expected: 0 Received: 1` on the button count, where the extra button was the alert's
5. **Correct decision.** Validate new E2E tests against a known-good target, scope locators to application elements,
   and wait for asynchronous state instead of asserting it synchronously.
6. **Fix (done).**
   - Added `appAlert(page)` (`.alert[role=alert]`).
   - Click, then `expect(...).toBeChecked()`.
   - Count transition buttons by name.
   - With the port issue from AI-6 already aligned, the stub run went from 18/20 to 20/21 passing (one test was added
     for the uncovered defect). The remaining failure is a real UI defect (stale conflict message), now recorded as
     I-2 / m-1 rather than hidden.
   - Before the port was aligned, the stub run was 1/20, but those failures were AI-6, not test defects.
7. **Prevention.** Add to `rules/testing.md` §6: *"New E2E tests must pass against a known-good target (reference stub or
   working build) before their failures are used to judge the system under test."*

## AI-9 — Concurrency tests that pass whether or not the race is handled

1. **AI output.**
   - `spec/test-strategy.md` TS-SM-C1/C2: "two concurrent transitions … released together by a latch → exactly one
     200, one 409 … repeated 20× to expose races".
   - The generated `StatusTransitionApiIT.concurrentIdenticalTransitions` uses `CompletableFuture.supplyAsync` twice.
2. **Why incorrect.** If the requests run one after the other, the second fails the early version check with the same
   409. The test can't show whether the commit-time optimistic lock (`state-machine.md` §9.1, layer 2) works, which is
   the part protecting against lost updates. Twenty repetitions add runtime, not certainty. The generated version also
   uses `join()` without a timeout.
3. **How detected.** Spec review SR-09, and code review m-13.
4. **Evidence.** Reasoning over both interleavings gives an identical observable outcome (one `200`, one `409`, final
   `version + 1`) whether or not layer 2 exists.
5. **Correct decision.** Force the interleaving deterministically: a test-only hook after load and version check, before
   flush, so both transactions pass layer 1 before either commits. Then assert which layer rejected the loser.
6. **Fix (partly done).** The generated test states its limitation in a comment and dropped the "20×" loop. The hook is
   planned in STEP-09 and STEP-41 and isn't built yet.
7. **Prevention.** Add to `rules/testing.md` §3: *"A concurrency test must state the interleaving it forces and fail
   when the protection it targets is removed. Sleep- or repetition-based race tests are not accepted."*

---

## 10. Patterns across the issues

| Pattern | Issues | Countermeasure now in effect |
|---------|--------|------------------------------|
| Plausible framework behaviour stated without checking the pinned version | AI-1, AI-3, AI-6 | Executable spikes and dependency-graph checks. STEP-04 "re-check every named API" |
| Tests that could not fail (or couldn't fail for the stated reason) | AI-5, AI-7, AI-9, AI-6 (integration check) | "A test never seen failing is not trusted", plus decoys and payload assertions |
| Internal contradictions in long generated specs | AI-3, AI-4 | Mandatory `review-spec` pass with cross-section checks before implementation |
| Unvalidated tool output written to files | AI-2 | Validate format before writing, and show written files |

## 11. Considered and not counted as AI mistakes

- **Choosing versions without STEP-04, and building STEP-07 before Phase 0:** the AI flagged the unmet dependency and
  asked. The engineer instructed it to proceed. This is a recorded project decision, not an AI error (the consequences
  are tracked in AI-3).
- **Implementing frontend steps 46–58 in one go:** the request explicitly listed all those features. The scope was
  reported, not hidden.
- **Moving `docs/requirements.md` to `spec/`:** it followed the paths the engineer used in their prompt, and every
  reference was updated.
- **Code-review finding M-2 (search box fights URL navigation):** a genuine AI-introduced defect. It is omitted here only
  because it has the same root cause as AI-7 (UI state not re-synced with server or URL state, and no test for it). It's
  tracked in `docs/reviews/2026-09-26-code-review.md`.

## 12. Engineer sign-off

The engineer (repository owner) completes this section when the open items are decided.

| Item | Decision / owner | Date |
|------|------------------|------|
| AI-3, AI-4 contract semantics (STEP-02) | | |
| AI-6 runtime proxy | | |
| AI-7 lost-update fix | | |
| Adopt the preventive rules above into `rules/` and `commands/` | | |
