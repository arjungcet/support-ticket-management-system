# Specification Review — 2026-09-26

| Scope | Reviewer stance | Verdict |
|-------|-----------------|---------|
| `spec/requirements.md`, `architecture.md`, `data-model.md`, `api-contract.md`, `state-machine.md`, `test-strategy.md` (+ the `rules/` they cite) | Skeptical senior engineer. Nothing modified | **Not ready for implementation.** 4 blockers, 14 major, 14 minor |

Severity:
- **Blocker:** the spec is contradictory or can't be implemented as written.
- **Major:** it would produce a defect, a flaky test or a security gap.
- **Minor:** inconsistency or ambiguity worth fixing before code.

Line numbers refer to the files as of this review.

---

## Blockers

### SR-01 — Phase 3 (functional spec) was skipped. Unconfirmed product guesses are now encoded as test oracles
- **Issue:** `spec/functional-spec.md` does not exist. The downstream documents carry more than 60 unresolved
  assumptions (A-1…34, DM-1…9, API-1…7, SM-1…4, TS-1…6). The product-level ones are stated in `test-strategy.md` as
  **expected results**, for example:
  - TS-ERR-09: edits to `CLOSED` tickets → 422 (A-17)
  - TS-SM-R4: reopen rejected (A-20)
  - TS-SM-R8: self-transition → 409 (A-21)
  - TS-SRCH-13: comments not searched (A-24)
  - TS-FILT-06: terminal tickets listed by default (API-2)
  - DM-3: description mandatory
- **Why it matters:** tests will "prove" behaviour nobody approved. If the product owner answers differently, the
  contract, data model (e.g. `ck_ticket_status_timestamps` for reopen), state machine and tests all change together.
  That is the rework SDD is meant to prevent. It also violates `rules/ai-assisted-development.md` §1 (phase order).
- **Correction:** write `spec/functional-spec.md` from the template. Answer at least A-4, A-13, A-14, A-15, A-17,
  A-18, A-19, A-20, A-21, A-24, A-30, DM-1…DM-8, API-1, API-2, API-7, SM-2…SM-4. Mark each downstream assumption
  confirmed or changed. Until then, label affected test cases "provisional".
- **Files:** all under `spec/`. Primarily `requirements.md`, `test-strategy.md`.

### SR-02 — PATCH and PUT-assignee still need absent-vs-null ("tri-state") handling, contradicting the claim that they don't
- **Issue:**
  - `api-contract.md:268` and `architecture.md:404` say splitting out the assignee endpoint removes tri-state handling.
  - But `api-contract.md:426` says PATCH fields may be **absent** (unchanged) but **`null` is not allowed**
    (→ `REQUIRED`), and TS-VAL-01/03 test exactly that.
  - `PUT /assignee` (`api-contract.md:469`, API-3) says the key must be **present**, and `null` means unassign
    (TS-VAL-04 "key missing → REQUIRED").
  - Both need to tell a missing property apart from an explicit `null`. With plain Java records and Bean Validation,
    the two are indistinguishable.
- **Why it matters:** as written, the behaviour can't be built with the DTO strategy in `architecture.md` §10.
  The implementer will either quietly treat `null` as absent (so the tests fail) or add the tri-state type the design
  says isn't needed. Clients built on JSON Merge Patch (RFC 7396) libraries also send `null` to mean "remove".
- **Correction:** choose one and state it in all three files.
  - **(a) Recommended:**
    - PATCH: `null` is treated exactly like absent (unchanged). Drop the "`null` → `REQUIRED`" cases.
    - PUT assignee: an absent key is treated as `null` (unassign). Drop API-3.
  - **(b):** keep the strict rules and reintroduce a tri-state wrapper (e.g. `JsonNullable`) in the architecture,
    listing the dependency.
- **Files:** `api-contract.md` §5, §6.4, §6.5, §9 (API-3). `architecture.md` §10. `test-strategy.md` TS-VAL-01, 03, 04.

### SR-03 — "All field errors reported together" can't hold for unknown fields, bad enums or wrong JSON types
- **Issue:**
  - `api-contract.md:58` promises that **all** field errors are reported together in one `VALIDATION_FAILED`.
    TS-VAL-10 tests blank title + too-long description + bad priority as 3 tuples.
  - But unknown properties (with `FAIL_ON_UNKNOWN_PROPERTIES=true`, per `rules/api-standards.md` §4) and invalid enum
    values (`"priority":"CRITICAL"`) fail inside **Jackson deserialisation**, before Bean Validation runs. Jackson
    stops at the first problem, so only one error can be reported and nothing from Bean Validation.
  - Separately, `api-contract.md:110` says `"title": 123` and `"version": "3"` are `MALFORMED_REQUEST`. Jackson 2's
    default scalar coercion **accepts** both, turning them into `"123"` and `3`.
- **Why it matters:** TS-VAL-06, 08, 10 and 31 will fail against a default Spring setup. The UI would see different
  error shapes depending on which mistake the user made.
- **Correction:** specify the binding strategy explicitly.
  - Either bind enums and collect unknown properties in a way that feeds Bean Validation (enum fields as `String` with
    an enum-membership constraint, unknown keys captured with `@JsonAnySetter` and reported as `UNKNOWN_FIELD`), so
    everything is reported together.
  - Or state in the contract that deserialisation-level problems (unknown field, bad enum, wrong type) are reported
    **alone**, as a single-entry `VALIDATION_FAILED`.
  - In both cases, add "scalar coercion disabled" (strict types) to the Jackson configuration in
    `architecture.md` §17 and to `rules/api-standards.md` §4.
- **Files:** `api-contract.md` §1.1, §1.3, §2.1, §2.2. `architecture.md` §11, §17. `test-strategy.md` TS-VAL-06, 08, 10, 31.
  (Also `rules/api-standards.md` §4.)

### SR-04 — Two "sources of truth" for the API, and the machine-readable one doesn't exist yet
- **Issue:**
  - `api-contract.md` calls itself **normative** and says it wins over `openapi.yaml`.
  - `rules/api-standards.md` §7 and `architecture.md` §3/§15.3 call `openapi.yaml` the canonical machine-readable
    contract.
  - Frontend types (`architecture.md` §6.1 `schema.d.ts`), MSW fixtures (`rules/testing.md` §6) and contract
    validation (TS-INT-04, TS-2) all depend on `openapi.yaml`, which has not been written.
  - The OAS-3.1 support of the validator is unverified (TS-2).
- **Why it matters:** two hand-maintained descriptions of the same API will drift. The frontend phase and TS-INT-04
  can't start, and a test that validates against a file that differs from the "normative" markdown proves nothing.
- **Correction:**
  - Make `openapi.yaml` the **single** normative contract.
  - Reduce `api-contract.md` to human-readable rationale, rules and examples that reference the schema names, or
    generate it from the YAML.
  - Write the YAML before the implementation plan, and pick and verify the validator library now.
- **Files:** `api-contract.md` (header, §8.1). `architecture.md` §3, §6.1, §15.3. `test-strategy.md` TS-INT-04, §17 TS-2.

---

## Major

### SR-05 — Case folding depends on an unspecified database collation, and the architecture and data model disagree on where lowering happens
- **Issue:**
  - `architecture.md:372` lowers both sides in SQL: `lower(col) LIKE lower(:pattern)`.
  - `data-model.md:334` lowers the pattern in **Java** and the column in SQL.
  - Java `toLowerCase` and PostgreSQL `lower()` differ for non-ASCII characters (e.g. `İ` → `i̇`, locale-dependent
    rules).
  - PostgreSQL `lower()` in the `C` collation only folds ASCII. Neither the database encoding nor the collation is
    specified for production, docker-compose or Testcontainers.
  - TS-SRCH-08 (`café`/`CAFÉ`) passes or fails depending on how the container was initialised.
- **Why it matters:** search results would differ between environments, the test would be flaky across CI images,
  and results could be silently wrong in production.
- **Correction:**
  - Specify `ENCODING 'UTF8'` and an explicit collation (e.g. ICU `und-x-icu` or `en_US.UTF-8`) for production, local
    and test databases, and pin it in the Testcontainers setup.
  - Lower **both** sides in SQL (`lower(:pattern)`) so one engine does all case folding.
  - State that search is case-insensitive for scripts supported by that collation.
- **Files:** `data-model.md` §12, §13.1, §14. `architecture.md` §9, §12. `test-strategy.md` §9 TS-SRCH-08.

### SR-06 — NUL/control characters and "whitespace" are undefined, which causes 500s and blank-but-accepted values
- **Issue:**
  - **NUL bytes.** JSON `"\u0000"` is valid input. PostgreSQL rejects NUL in `varchar` with an encoding error, so the
    request becomes `500 INTERNAL_ERROR`. H2 accepts it.
  - **Inconsistent whitespace definitions.** "Trim" and "blank" mean different things in each layer:
    - Java `String.trim()` strips everything up to U+0020.
    - Java `strip()` strips Unicode whitespace, but not NBSP (U+00A0).
    - SQL `TRIM()` (the `ck_*_not_blank` constraints) strips **spaces only**.
  - So a title of `" "` (NBSP) passes every layer as non-blank. A domain path that skips normalisation could store
    `"\t"`, which passes the DB check.
  - Newlines in `title`, `assignee` or `author` are neither allowed nor forbidden.
- **Why it matters:** a trivially reachable 500 contradicts REQ-9/REQ-10 ("validate", "meaningful errors"), and
  "blank" is not objectively testable.
- **Correction:**
  - Define the accepted character set: reject `U+0000` and other C0 control characters except `\t\n\r` in multi-line
    fields (→ `INVALID_VALUE`). Forbid line breaks in single-line fields.
  - Define "whitespace" as Unicode White_Space including NBSP.
  - Specify the Java normalisation method, and make the DB `CHECK` at least reject whitespace/control-only values
    using portable SQL (or accept that the DB check is weaker and say so).
  - Add test cases.
- **Files:** `api-contract.md` §1.1, §2.2, §2.3. `data-model.md` §7, §10. `architecture.md` §11. `test-strategy.md` §7.

### SR-07 — H2 length semantics claimed to match PostgreSQL. They likely don't
- **Issue:** `data-model.md:375` states that `varchar(n)` means "n characters" on both databases, with no difference.
  H2 stores Java strings and measures length in UTF-16 code units, like Bean Validation's `@Size`. PostgreSQL counts
  code points. A 200-emoji title fits in PostgreSQL but exceeds `varchar(200)` in H2. (Verify against the chosen H2
  version.)
- **Why it matters:** the compatibility table is the document that is supposed to prevent exactly this kind of surprise.
  The H2 profile would reject data that production accepts.
- **Correction:** verify the behaviour, fix the table row, and note that the API limit (UTF-16, per DM-5) is the
  effective limit in both databases, so the difference is harmless only because the API validates first.
- **Files:** `data-model.md` §7, §14.

### SR-08 — A comment can be added to a ticket that is being closed at the same moment, breaking the "closed tickets take no comments" invariant
- **Issue:**
  - `state-machine.md:292` (C5) and `architecture.md` §16 (A-34) accept that "add comment" and "close" may interleave.
  - The comment transaction reads `IN_PROGRESS`/`RESOLVED`, the close commits, and then the comment commits. The
    ticket ends up `CLOSED` with a comment whose `created_at` is after `closed_at`.
- **Why it matters:** TS-ERR-09 asserts the rule, but production data can contradict it. Nothing detects this, and
  audit readers will see comments on closed tickets.
- **Correction:** have `addComment` load the ticket with `PESSIMISTIC_READ` (`SELECT … FOR SHARE`). A concurrent
  status `UPDATE` then waits for the comment transaction to finish, and a comment transaction that starts after the
  update sees the committed status. Comments still don't bump `version`. Add an integration test that forces this
  interleaving. Alternatively, document that the invariant is "best effort" and drop it from the tests.
- **Files:** `state-machine.md` §9.2 C5. `architecture.md` §16. `api-contract.md` §6.6. `test-strategy.md` §6.4.

### SR-09 — The concurrency tests don't exercise the race they claim to test
- **Issue:** TS-SM-C1/C2 (`test-strategy.md:245`) release two requests "together" and expect one `200` and one `409`,
  "repeated 20× to expose races".
  - If the requests happen to run **one after the other**, the second fails the early version check and returns the
    same `409`.
  - So the test passes whether or not the commit-time optimistic lock (layer 2, `state-machine.md` §9.1) works.
  - Twenty repetitions add runtime, not certainty.
- **Why it matters:** layer 2 is the part that protects against lost updates, and it would be untested at the API
  level. The test gives false confidence.
- **Correction:** force the interleaving deterministically. Add a test-only hook, such as a `CyclicBarrier` bean
  called after the load and version check but before the flush, so both transactions pass layer 1 before either
  commits. Assert that the loser got its `409` from the commit-time path (e.g. via a log or a metric). Keep TS-REPO-06
  as the lower-level proof, and remove "repeat 20×".
- **Files:** `test-strategy.md` §6.4. `state-machine.md` §12.4.

### SR-10 — `currentVersion` in the 409 response is not available when the conflict is detected at commit time
- **Issue:** `api-contract.md:115` lists `currentVersion` as an extension of `TICKET_CONCURRENT_MODIFICATION`, and
  TS-ERR-03 asserts it. When the conflict surfaces as Hibernate's optimistic-lock exception at flush or commit, the
  transaction has already rolled back and the current version is unknown without a fresh read in a new transaction.
- **Why it matters:** the contract promises a field the design can't always provide. Either tests fail on the layer-2
  path, or the implementation adds an unplanned extra query in the error handler.
- **Correction:** make `currentVersion` optional ("present when known"), or remove it. The UI refetches anyway
  (`api-contract.md` §8.1).
- **Files:** `api-contract.md` §2.1. `state-machine.md` §8. `test-strategy.md` TS-ERR-03.

### SR-11 — The `Location` header will contain the backend's internal host when requests come through the Next.js proxy
- **Issue:** the contract specifies `Location: /api/v1/tickets/{id}`, a relative path (`api-contract.md:304`, `:513`).
  The usual Spring approach (`ServletUriComponentsBuilder.fromCurrentRequest()`) builds an **absolute** URL from the
  request it receives. Behind the Next.js rewrite (`architecture.md` §2), that is the backend's own address (e.g.
  `http://backend:8080/…`), unless forwarded headers are handled. Neither document specifies
  `server.forward-headers-strategy` or relative `Location` values.
- **Why it matters:** clients following the header would call an address the browser can't reach, and the contract
  and tests would disagree with the running system.
- **Correction:** state that `Location` is a **relative** URI (path only). Add this to `architecture.md` §17
  configuration, and to the integration tests (assert the exact header value).
- **Files:** `api-contract.md` §6.1, §6.6. `architecture.md` §2, §17. `test-strategy.md` TS-WEB-02.

### SR-12 — No authentication, and no stated boundary that compensates for it
- **Issue:**
  - A-4 removes authentication, and nothing replaces it. Anyone who can reach the API can cancel or close any ticket.
    That is irreversible, because terminal states have no exit.
  - Anyone can post comments under any `author` name (A-14) and change assignees.
  - There is no request-size limit, even though `rules/security.md` §2 requires one, and no rate limiting.
  - Ticket ids are sequential, so every ticket can be enumerated.
  - The requirements don't say who uses the system or where it is deployed.
- **Why it matters:** for a support tool (customer data in descriptions and comments), this is a data-integrity and
  confidentiality risk. The comment trail is not trustworthy, and it isn't a documented, accepted risk.
- **Correction:**
  - Add a security section to the functional spec: v1 is deployed only on a trusted internal network, or behind a
    reverse proxy with SSO. Record that as an accepted-risk decision record.
  - Specify a maximum request body size (e.g. 64 KB → `413 PAYLOAD_TOO_LARGE`, a new error code) and rate limiting
    at the proxy.
  - Label `author` in the UI and API as "unverified".
  - Keep the extension point in `architecture.md` §19.
- **Files:** `requirements.md`. `architecture.md` §2, §19, §20 (A-4). `api-contract.md` §1.1, §2.1.

### SR-13 — REQ-4 edit flow is split across two versioned requests with no defined UI sequence
- **Issue:** editing the title and the assignee in one form now needs `PATCH` then `PUT /assignee`. The second request
  must use the version returned by the first. If the second fails (422 or 409), the first is already committed.
  Neither the architecture nor the tests (TS-FE-09/10) define the sequencing, how the version is passed along, or
  what the user sees after a partial failure.
- **Why it matters:** there is a real risk of confusing partial saves, or of spurious `409`s if the UI sends both
  requests with the original version.
- **Correction:** either (a) make the assignee a separate, immediately saved control in the UI (so it never shares a
  form with the other fields) and specify that, or (b) allow `assignee` in `PATCH` (which requires the SR-02 decision).
  Add a test for the chosen flow.
- **Files:** `api-contract.md` §5, §6.4, §6.5. `architecture.md` §6. `test-strategy.md` TS-FE-09, TS-FE-10.

### SR-14 — The 409/422 split and the no-op rules follow no single principle
- **Issue:**
  - Rejections that depend on the ticket's current state are split across two status codes:
    - `CLOSED → OPEN` → **409** `TICKET_INVALID_TRANSITION`
    - editing a `CLOSED` ticket → **422** `TICKET_NOT_EDITABLE`
    - commenting on it → **422** `TICKET_NOT_COMMENTABLE`

    The justification in `api-contract.md` §2.1 ("409 = refetch helps") is false for `CLOSED → OPEN`: refetching
    never makes it legal.
  - The no-op rules also disagree: a PATCH that changes nothing → `200` (§1.2), a self-transition → `409`
    (A-21, rows 1/7/13).
  - It is undefined whether a no-op PATCH on a `CLOSED` ticket returns `200` or `422`.
- **Why it matters:** clients and tests have to memorise exceptions instead of applying a rule. The frontend's "409 →
  refetch" behaviour loops pointlessly for terminal-state rejections.
- **Correction:**
  - Adopt one rule, e.g. "all rejections caused by the ticket's current state → 409 with a specific `code`". Keep 422
    unused or reserved.
  - Make the no-op rules consistent (either self-transition → `200` unchanged, or no-op PATCH → `200` explicitly
    justified).
  - Define the precedence: terminal-state check before no-op detection.
- **Files:** `api-contract.md` §1.2, §2.1, §6.4. `state-machine.md` §4. `architecture.md` §14.2. `test-strategy.md` TS-ERR-09, TS-SM-R8.

### SR-15 — Search tests for `%` and `_` pass even without escaping
- **Issue:** the seed data (`test-strategy.md:330`) has only `"Discount 100% not applied"` and
  `"Promo code_2026 rejected"`.
  - An **unescaped** `%100%%` pattern still matches only ticket 3.
  - An unescaped `code_2026` also matches only ticket 3, because no `codeX2026` decoy exists, even though the table
    itself says it "would match `codeX2026`".
- **Why it matters:** TS-SRCH-04 and TS-SRCH-05 can't fail when escaping is broken. That is exactly the defect they
  exist to catch, and SQL-wildcard bugs would ship.
- **Correction:** add decoy rows, e.g. `"Order 1000 items"` (matched by an unescaped `100%`) and
  `"Promo codeX2026"` (matched by an unescaped `code_2026`). Assert the decoys are **excluded**. Verify by temporarily
  removing escaping.
- **Files:** `test-strategy.md` §9.

### SR-16 — `updated_at >= created_at` CHECK plus timestamps from each node's own clock gives a 500 under clock skew
- **Issue:** timestamps come from each application node's `Clock` (`data-model.md` §11), and the architecture claims
  horizontal scaling (§19). If node B's clock is behind node A's, an update on B right after a create on A produces
  `updated_at < created_at` and violates `ck_ticket_updated_after_created`. The same applies to lifecycle timestamps
  and to comment `created_at`, whose ordering is unchecked and could appear before the ticket's `created_at`.
- **Why it matters:** an environment-dependent `500`, and ordering guarantees the UI relies on (comments sorted by
  time) that can be violated.
- **Correction:** in the domain, clamp to `max(clock.now, previous timestamp)` for `updatedAt` and lifecycle
  timestamps, and document it. Or drop the CHECK and state "clocks are NTP-synchronised" as an operational
  requirement. Comment order already ties on `id`, so state that `id` is the authoritative order.
- **Files:** `data-model.md` §10, §11. `architecture.md` §19. `state-machine.md` §10.

### SR-17 — Several requirements are not objectively testable
- **Issue:**
  - REQ-10 "Display meaningful errors in the UI" has no acceptance criterion. `architecture.md` §6.2 and
    `api-contract.md` §8.1 describe *mechanisms*, but no text per error `code`.
  - Performance statements are unquantified:
    - `data-model.md:354`: "well within interactive latency"
    - `architecture.md` §1: "modest"
    - A-1: "≤ 100k tickets, tens of users", with no latency target
  - Accessibility has no WCAG level (TS-FE-13 checks only that axe reports no violations). There is no browser
    support statement.
- **Why it matters:** "meaningful" and "interactive" can't pass or fail a test, and `test-strategy.md` §16 already
  admits performance is untested.
- **Correction:**
  - Add a UI message catalogue (code → user message → placement) to the functional spec, and test against it.
  - Add NFRs: p95 list/search latency at N tickets, maximum page size, WCAG 2.1 AA, supported browsers.
- **Files:** `requirements.md`. `architecture.md` §1, §6.2. `data-model.md` §13.3. `api-contract.md` §8.1. `test-strategy.md` §12, §16.

### SR-18 — Missing requirements a support-ticket system normally needs
- **Issue:** no requirement or decision for:
  - **reporter/requester** (who raised the ticket, and how to contact them)
  - **who** performed a change (no actor on status changes or edits)
  - status-change **history** (SM-2)
  - delete or archive (API-7)
  - time-zone display in the UI
  - the default list view: including closed tickets by default (API-2) will bury active work
  - where the comment author name comes from in the UI when there are no users
- **Why it matters:** each of these changes the data model (new columns or tables) or the API. Adding them after
  implementation means migrations and contract version churn.
- **Correction:** have the functional spec explicitly include or exclude each item, with a reason.
- **Files:** `requirements.md`. `data-model.md` §1, §3. `api-contract.md` §6.2.2. `architecture.md` §6.

---

## Minor

### SR-19 — A comment's `Location` header points at a URL that doesn't exist
- **Issue:** `POST /comments` returns `Location: …/comments/{commentId}` (API-4), but no `GET` exists for a single comment. The URL returns 404 or 405.
- **Why:** it misleads clients and fails any automated "follow the Location header" check.
- **Correction:** omit the header, or add `GET /tickets/{id}/comments/{commentId}`.
- **Files:** `api-contract.md` §6.6, §9.

### SR-20 — Timestamp string format is not fixed
- **Issue:** the examples use `…09:15:30.5Z` (`api-contract.md:210`). Java's `Instant` serialises in 0, 3 or 6 fractional digits (`.500Z`), and the contract says only "µs at most".
- **Why:** tests or clients that compare strings will be brittle, and the example can never actually be produced.
- **Correction:** fix the format (e.g. always 6 fractional digits), or state "compare as instants, not strings". Correct the examples.
- **Files:** `api-contract.md` §1.1, §4.1. `state-machine.md` §7.1.

### SR-21 — `int64` ids and versions exceed JavaScript's safe-integer range
- **Issue:** the contract types `id`, `version` and `ticketId` as int64. JavaScript numbers are only exact up to 2⁵³.
- **Why:** it's theoretical at this scale, but generated TypeScript types will say `number` and silently lose precision beyond that.
- **Correction:** state a maximum of 2⁵³−1, or serialise as strings. Record the choice.
- **Files:** `api-contract.md` §1.1, §4.

### SR-22 — Behaviour of repeated `sort` and misspelled query parameters is undefined
- **Issue:** Spring accepts several `sort` parameters, but the contract defines one. Unknown parameters are silently ignored, so `staus=OPEN` returns every ticket.
- **Why:** ambiguous, and client typos go unnoticed.
- **Correction:** specify "only the first `sort` is used" (or reject extras with `400`). Consider rejecting unknown list parameters.
- **Files:** `api-contract.md` §1.1, §6.2.

### SR-23 — Error catalogue gaps
- **Issue:** no code for `406 Not Acceptable` (`Accept: text/html`), `413` (see SR-12), or a POST with a missing `Content-Type` and an empty body. TS-VAL-30 expects `MALFORMED_REQUEST` for an empty body, but Spring may answer `415` first.
- **Why:** these responses would be untyped and untested.
- **Correction:** add `NOT_ACCEPTABLE` and `PAYLOAD_TOO_LARGE`, and define the empty-body and missing-type precedence.
- **Files:** `api-contract.md` §1.3, §2.1. `test-strategy.md` TS-VAL-30.

### SR-24 — Spring Boot version is unpinned, while the specs rely on version-specific APIs
- **Issue:** A-5 says "latest GA". The specs name `@MockitoBean`, `@ServiceConnection`, Jackson 2 `FAIL_ON_UNKNOWN_PROPERTIES`, built-in structured logging, and Testcontainers module names. A major Boot upgrade (e.g. Jackson 3 packages and settings, Testcontainers 2 package moves) changes several of these.
- **Why:** it's the source of "hallucinated API" defects (`rules/ai-assisted-development.md` §3).
- **Correction:** pin the Boot, Hibernate, Jackson, Flyway and Testcontainers major versions in a decision record before the implementation plan, then re-check every API named in the specs against them.
- **Files:** `architecture.md` §3, §17, §18, §20 (A-5). `test-strategy.md` §4.

### SR-25 — `spring.mvc.problemdetails.enabled=true` conflicts with the custom error handler
- **Issue:** Boot's auto-configured `ProblemDetailsExceptionHandler` backs off when the custom `ResponseEntityExceptionHandler` advice exists, so the property does nothing (`architecture.md:594`).
- **Why:** it confuses readers about which component produces errors.
- **Correction:** remove the property and state that `GlobalExceptionHandler` is the only producer.
- **Files:** `architecture.md` §14.1, §17.

### SR-26 — Cross-document inconsistencies
- **Issue:**
  - Migration path: `architecture.md:94` has `db/migration/V1…`, while `data-model.md` §12.2 has `db/migration/common/`.
  - Column types: data-model tables say `timestamptz`, while §14 says write `TIMESTAMP WITH TIME ZONE`.
  - Lowering: see SR-05.
  - `rules/java-springboot.md:113` says "every table" has `updated_at` and `version`, contradicting append-only `ticket_comment`.
- **Why:** implementers pick whichever they read first.
- **Correction:** align the documents, and change the rule to "every **mutable** table".
- **Files:** `architecture.md` §4. `data-model.md` §3, §4, §14. (`rules/java-springboot.md` §5.)

### SR-27 — `updatedAt` sort ignores comment activity
- **Issue:** DM-6 means `sort=updatedAt,desc` doesn't surface tickets that just received comments.
- **Why:** users will read "recently updated" as "recent activity". It's ambiguous in the UI.
- **Correction:** name the UI sort "last modified". Alternatively, add `lastActivityAt` (a spec decision).
- **Files:** `data-model.md` §11. `api-contract.md` §3.

### SR-28 — TS-INT-03 relies on a failure-injection hook the architecture doesn't provide
- **Issue:** the test needs "a test-only failing bean after the domain change but before commit", and no such hook exists in the design.
- **Why:** the test is unimplementable as written, or leads to test code inside production classes.
- **Correction:** define the hook, e.g. a no-op `TransactionPhaseHook` interface in production, replaced by a failing bean in tests (same hook as SR-09), or cut the test.
- **Files:** `test-strategy.md` §4.5. `architecture.md` §16.

### SR-29 — How the UI collects the comment author is unspecified, and untested
- **Issue:** `author` is required (A-14), but `architecture.md` §6 and TS-FE-11 never say how the UI gets it: a text field, a remembered name, or a prompt.
- **Why:** the frontend/backend contract has a gap, and the first real comment submission may hit a `400`.
- **Correction:** specify an author text field, remembered per browser, and add it to TS-FE-11.
- **Files:** `architecture.md` §6. `test-strategy.md` TS-FE-11.

### SR-30 — PATCH that changes nothing isn't prevented in the UI
- **Issue:** a PATCH with only `version` returns `400 NO_CHANGES_REQUESTED`, but TS-FE-09 doesn't cover a user pressing Save without edits.
- **Why:** users see a pointless validation error.
- **Correction:** disable Save until something changes, and test it.
- **Files:** `test-strategy.md` TS-FE-09. `architecture.md` §6.

### SR-31 — The UI's default list filter is unspecified
- **Issue:** API-2 includes terminal tickets when no filter is given, and `architecture.md` §6 doesn't say whether the UI defaults to active statuses.
- **Why:** it's a user-visible choice that drives TS-FE-04 and TS-E2E-04 expectations.
- **Correction:** decide in the functional spec, e.g. UI default `status=OPEN,IN_PROGRESS,RESOLVED`.
- **Files:** `api-contract.md` §6.2.2. `architecture.md` §6.

### SR-32 — Architecture rule E5 in the state-machine doc is too broad as worded
- **Issue:** "Anything outside `ticket.application` depending on `TicketRepository` is forbidden" would also forbid the repository and integration tests.
- **Why:** the ArchUnit rule would fail on day one, or be weakened ad hoc.
- **Correction:** scope it to production classes (`ImportOption.DoNotIncludeTests`).
- **Files:** `state-machine.md` §5, §12.5. `test-strategy.md` TS-ARCH.

---

## Summary by category

| Category | Issues |
|----------|--------|
| Missing requirements | SR-01, SR-12, SR-17, SR-18, SR-29, SR-31 |
| Contradictions | SR-02, SR-03, SR-04, SR-07, SR-14, SR-25, SR-26 |
| Ambiguous behaviour | SR-06, SR-13, SR-14, SR-20, SR-22, SR-27, SR-30 |
| Incorrect assumptions | SR-03 (Jackson coercion), SR-07 (H2 lengths), SR-10, SR-11, SR-24 |
| API inconsistencies | SR-02, SR-10, SR-14, SR-19, SR-21, SR-22, SR-23 |
| Database problems | SR-05, SR-06, SR-16 |
| State-machine gaps | SR-08, SR-14, SR-32 |
| Validation gaps | SR-03, SR-06 |
| Testing gaps | SR-09, SR-15, SR-28, SR-01 (provisional oracles) |
| Security | SR-12, SR-06 (500 via NUL) |
| FE/BE contract mismatches | SR-04, SR-11, SR-13, SR-21, SR-29, SR-30 |
| PostgreSQL/H2 incompatibilities | SR-05, SR-06, SR-07 |
| Not objectively testable | SR-17, SR-06 ("blank") |

## Recommended order of resolution

1. SR-01: write the functional spec. It answers most product questions behind SR-12, 14, 17, 18, 29 and 31.
2. SR-02, SR-03, SR-10, SR-14, SR-19, SR-23: settle the contract semantics, then SR-04 (write `openapi.yaml` as the single source).
3. SR-05, SR-06, SR-07, SR-16, SR-26: database and portability fixes.
4. SR-08, SR-09, SR-15, SR-28, SR-32: state-machine and test corrections.
5. SR-24: pin versions before the implementation plan.
