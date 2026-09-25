# Requirements — Support Ticket Management System

| Status | Last updated | Related |
|--------|--------------|---------|
| Explicit requirements: **approved** (as given by the product owner). Assumptions: **pending sign-off** (§15) | 2026-09-26 | [`architecture.md`](architecture.md), [`api-contract.md`](api-contract.md), [`state-machine.md`](state-machine.md), [`data-model.md`](data-model.md), [`test-strategy.md`](test-strategy.md), [spec review](../docs/reviews/2026-09-26-spec-review.md), [`docs/prompt-history.md`](../docs/prompt-history.md) |

This is the requirements analysis (SDD milestone 02, guide Prompt 1). It separates what the product owner **stated**
from what the team **assumed**, what is **unknown**, and what must be **decided**. Nothing here is silently invented.

| Marker | Meaning |
|--------|---------|
| **E** — Explicit | Stated by the product owner. The source prompt is cited ("Prompt 0" = project initialisation, "AC prompt" = final acceptance criteria, "State-machine prompt", "Frontend prompt", "Test prompt"). |
| **A** — Assumption | A reasonable default the team chose where the requirements are silent. Status: **implemented** (the code relies on it, so a change needs spec, code and tests) or **provisional** (documented, not relied on). Ids (A-n, DM-n, API-n, SM-n) come from the spec documents. |
| **Q** — Open question | Needs a product-owner answer. No default is implied. |
| **D** — Decision needed | A choice the team must make or confirm before further implementation or release. |

> **History note.** This analysis was written **after** the design documents and the implementation. The design phases
> had to record their own assumptions (see the spec review, SR-01). This document consolidates them in one place so
> they can be signed off. It changes no behaviour. The REQ ids are unchanged.

---

## 1. Functional requirements (E)

| ID | Requirement | Source |
|----|-------------|--------|
| REQ-1 | Create a ticket. | Prompt 0 |
| REQ-2 | List tickets. | Prompt 0 |
| REQ-3 | View ticket details. | Prompt 0 |
| REQ-4 | Update title, description, priority and assignee. | Prompt 0 |
| REQ-5 | Add comments. | Prompt 0 |
| REQ-6 | Search tickets by keyword. | Prompt 0 |
| REQ-7 | Filter tickets by status. | Prompt 0 |
| REQ-8 | Persist data in a database. | Prompt 0 |
| REQ-9 | Validate input at the backend. | Prompt 0 |
| REQ-10 | Display meaningful errors in the UI. | Prompt 0 |
| REQ-11 | Allowed transitions: `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `RESOLVED → CLOSED`, `OPEN → CANCELLED`, `IN_PROGRESS → CANCELLED`. | Prompt 0, state-machine prompt |
| REQ-12 | Every other transition MUST be rejected by the backend (examples: `CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN`), whether the request comes from the UI, a REST client, Postman, curl or another service. | Prompt 0, state-machine prompt |

### 1.1 Acceptance criteria (E, AC prompt)

| ID | Criterion | Traces to |
|----|-----------|-----------|
| AC-1 | Ticket can be created from UI. | REQ-1 |
| AC-2 | Tickets can be listed. | REQ-2 |
| AC-3 | Ticket details can be viewed. | REQ-3 |
| AC-4 | Ticket fields can be updated. | REQ-4 |
| AC-5 | Assignee can be changed. | REQ-4 |
| AC-6 | Comments can be added. | REQ-5 |
| AC-7 | Search works. | REQ-6 |
| AC-8 | Status filter works. | REQ-7 |
| AC-9 | Valid status transitions work. | REQ-11 |
| AC-10 | Invalid status transitions are rejected by backend. | REQ-12 |
| AC-11 | Data survives application restart. | REQ-8 |
| AC-12 | Backend validation works. | REQ-9 |
| AC-13 | UI shows meaningful errors. | REQ-10 |
| AC-14 | State-machine integration tests pass. | REQ-11, REQ-12 |
| AC-15 | No secrets are committed. | SEC-1 (§13) |

## 2. Non-functional requirements

### 2.1 Explicit (E)

| ID | Requirement | Source |
|----|-------------|--------|
| TC-1 | Java 21, Spring Boot, REST API. | Prompt 0 |
| TC-2 | PostgreSQL in production. H2 for tests/local lightweight execution where appropriate. | Prompt 0 |
| TC-3 | React / Next.js frontend. | Prompt 0 |
| TC-4 | Maven or Gradle. **Decided: Gradle, Kotlin DSL** (engineer, 2026-09-25). | Prompt 0 |
| TC-5 | JUnit 5, Spring Boot Test, Mockito and Testcontainers where appropriate. | Prompt 0 |
| NFR-1 | Data survives an application restart (durability). | AC prompt (AC-11) |
| NFR-2 | No secrets are committed to the repository. | AC prompt (AC-15), Prompt 0 ("security and secret handling") |

### 2.2 Not stated: assumptions and decisions

| ID | Topic | Status | Current default |
|----|-------|--------|-----------------|
| A-1 | Scale and performance | A, provisional | Internal tool, ≤ 100 k tickets, tens of concurrent users. **No latency target exists** (see D-4) |
| NFR-Q1 | Availability / uptime | Q | Not specified |
| NFR-Q2 | Accessibility level | Q | UI follows accessible patterns (labels, `aria-describedby`), but no WCAG level is required yet (see D-4) |
| NFR-Q3 | Supported browsers | Q | Evergreen desktop browsers assumed. E2E runs on Chromium only (TS-4) |
| NFR-Q4 | Localisation / time zones | Q | English UI. Timestamps shown in the browser's local time zone (not specified) |
| NFR-Q5 | Data retention / deletion | Q | Tickets and comments are never deleted (DM-1, DM-2, API-7) |

## 3. Actors

| Actor | Status | Description |
|-------|--------|-------------|
| **Support agent** (UI user) | A, implemented | Creates, views, edits, assigns, comments on, searches, filters and moves tickets through their lifecycle. No roles or permissions: every user can do everything (A-4) |
| **API client** (Postman, curl, another service) | E (state-machine prompt) | Calls the REST API directly. The backend must enforce every rule regardless of the client (REQ-12) |
| **Operator / engineer** | A, provisional | Deploys and configures the system (database, environment variables), reads logs using correlation ids |
| Customer / ticket requester | Q-1 | Not mentioned. Is there a person the ticket is *for*, distinct from the assignee? |

## 4. Main use cases

| ID | Use case | Actor | Requirements | Detailed in |
|----|----------|-------|--------------|-------------|
| UC-1 | Create a ticket (title, description, priority, optional assignee) | Agent | REQ-1, REQ-9 | api-contract §6.1 |
| UC-2 | Browse tickets page by page, sorted | Agent | REQ-2 | api-contract §6.2 |
| UC-3 | Search tickets by keyword | Agent | REQ-6 | api-contract §6.2.1 |
| UC-4 | Filter tickets by one or more statuses | Agent | REQ-7 | api-contract §6.2.2 |
| UC-5 | Open a ticket and read its details and comments | Agent | REQ-3 | api-contract §6.3, §6.3a |
| UC-6 | Edit title, description, priority | Agent | REQ-4 | api-contract §6.4 |
| UC-7 | Assign or unassign a ticket | Agent | REQ-4 | api-contract §6.5 |
| UC-8 | Add a comment | Agent | REQ-5 | api-contract §6.6 |
| UC-9 | Move a ticket through its lifecycle | Agent, API client | REQ-11, REQ-12 | state-machine.md, api-contract §6.9 |

## 5. Business rules

| ID | Rule | Status | Where |
|----|------|--------|-------|
| BR-1 | Allowed transitions are exactly those of REQ-11. | E | state-machine.md §3 |
| BR-2 | Every other transition is rejected by the backend, whatever the client. | E | state-machine.md §4–§5 |
| BR-3 | A new ticket starts in `OPEN`. Clients can't choose the initial status. | A, implemented (derived: REQ-11's lifecycle starts at OPEN) | state-machine.md §1 |
| BR-4 | `CLOSED` and `CANCELLED` are terminal: no outgoing transitions. | E (derived: REQ-11 lists no transition out of them) | state-machine.md §1 |
| BR-5 | A change to the same status (e.g. `OPEN → OPEN`) is rejected. | A-21, implemented | state-machine.md §4 |
| BR-6 | `RESOLVED → IN_PROGRESS` (reopen) is not allowed. | A-20, implemented (follows REQ-11 as written) | Q-2 |
| BR-7 | Title, description, priority and assignee can't be changed on `CLOSED`/`CANCELLED` tickets. They can while `RESOLVED`. | A-17, implemented | Q-3 |
| BR-8 | Comments can't be added to `CLOSED`/`CANCELLED` tickets. They can while `RESOLVED`. | A-18, implemented | Q-3 |
| BR-9 | Assigning a ticket doesn't change its status. No assignee is required to start work. | A-19, implemented | Q-4 |
| BR-10 | Lifecycle timestamps are recorded: `resolvedAt`, `closedAt`, `cancelledAt`. | A-22, implemented | data-model.md §11 |
| BR-11 | Concurrent edits are detected with a version number. A stale update is rejected, not silently overwritten. | A (design decision), implemented | api-contract §1.2 |
| BR-12 | Comments are append-only (no edit/delete). Tickets are never deleted. | DM-1, DM-2, API-7, implemented | Q-5 |
| BR-13 | Adding a comment doesn't change the ticket's version or "last modified" time. | A-34 / DM-6, implemented | Q-6 |

## 6. Validation rules

REQ-9 (E) requires backend validation. The specific rules below are **assumptions** (implemented), because the
requirements give no field limits.

| ID | Field | Rule | Status |
|----|-------|------|--------|
| VR-1 | Title | Required, 1–200 characters after trimming | A (DM-5), implemented |
| VR-2 | Description | Required, 1–5000 characters after trimming | A (DM-3, DM-5), implemented. Q-7: must description really be required? |
| VR-3 | Priority | One of `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Defaults to `MEDIUM` | A-15, implemented. Q-8: are these the right levels? |
| VR-4 | Assignee | Optional free text, ≤ 100 characters. Blank means unassigned | A-13, DM-4, implemented. Q-9 |
| VR-5 | Comment author | Required free text, ≤ 100 characters (unverified: no login) | A-14, implemented. Q-9 |
| VR-6 | Comment body | Required, 1–5000 characters after trimming | A (DM-5), implemented |
| VR-7 | Search keyword | ≤ 100 characters after trimming. Empty means no filter | A (DM-7), implemented |
| VR-8 | Status filter | Each value one of the five statuses. Several allowed | A (DM-8), implemented |
| VR-9 | All text | Leading and trailing whitespace is trimmed before validation and storage | A-30, implemented |
| VR-10 | Unknown fields, wrong JSON types | Rejected with a field-level or malformed-request error | A (api-contract §1.1), implemented. D-2 |

## 7. Error scenarios

REQ-10 (E) requires meaningful UI errors. The scenarios and their responses are **assumptions** made in the API
contract (implemented).

| ID | Scenario | Backend response | UI behaviour |
|----|----------|------------------|--------------|
| ES-1 | Invalid input (missing, blank, too long, unknown field, bad value) | `400 VALIDATION_FAILED` with every field error | Messages next to fields plus a summary |
| ES-2 | Unreadable request (malformed JSON, wrong types) | `400 MALFORMED_REQUEST` | Generic "could not be understood" |
| ES-3 | Ticket doesn't exist | `404 TICKET_NOT_FOUND` | "Ticket not found" page |
| ES-4 | Invalid status transition | `409 TICKET_INVALID_TRANSITION` with current/target/allowed | Reason shown, ticket reloaded |
| ES-5 | Ticket changed by someone else meanwhile | `409 TICKET_CONCURRENT_MODIFICATION` | "Changed by someone else" plus Reload. Input kept |
| ES-6 | Editing or commenting on a closed/cancelled ticket | `422 TICKET_NOT_EDITABLE` / `TICKET_NOT_COMMENTABLE` | Explanation. Controls hidden for terminal tickets |
| ES-7 | Wrong content type or method, unknown route | `415` / `405` / `404 RESOURCE_NOT_FOUND` | Generic message |
| ES-8 | Unexpected server failure | `500 INTERNAL_ERROR`, generic text plus correlation id | Retryable message with a support reference. Never raw error text |
| ES-9 | Backend unreachable | (proxy `502`) | Retryable "can't reach the server" / "something went wrong" |

## 8. State-machine rules (E)

```
OPEN → IN_PROGRESS → RESOLVED → CLOSED
OPEN → CANCELLED
IN_PROGRESS → CANCELLED
```

All other transitions are rejected (REQ-12). The complete 25-row matrix, API, error response, concurrency and
persistence behaviour are specified in [`state-machine.md`](state-machine.md). Rules not given by the product owner
(BR-3, BR-5…BR-10) are listed in §5 as assumptions.

## 9. Persistence requirements

| ID | Requirement | Status |
|----|-------------|--------|
| PR-1 | Data is persisted in a database (REQ-8). PostgreSQL in production (TC-2) | E |
| PR-2 | Data survives an application restart (AC-11) | E |
| PR-3 | H2 only for tests/local lightweight runs (TC-2). In-memory H2 is not durable, so durability is verified on PostgreSQL | E, plus A for the verification approach |
| PR-4 | Schema is versioned by migrations (Flyway) and validated at startup | A (architecture decision), implemented |
| PR-5 | Backups, retention, archival | Q (NFR-Q5) |

## 10. API requirements

| ID | Requirement | Status |
|----|-------------|--------|
| AR-1 | A REST API (TC-1) exposing REQ-1…REQ-7 and status transitions (REQ-11/12) | E |
| AR-2 | The API is the enforcement point for validation and the state machine (REQ-9, REQ-12) | E |
| AR-3 | Endpoints, payloads and one consistent error structure are defined in [`api-contract.md`](api-contract.md) (versioned under `/api/v1`, RFC 9457 errors) | A (design), implemented |
| AR-4 | A machine-readable contract (`openapi.yaml`) | D-3 |

## 11. Frontend requirements

| ID | Requirement | Status |
|----|-------------|--------|
| FR-1 | React / Next.js UI (TC-3) | E |
| FR-2 | Supports: create ticket, list, view details, edit fields, change assignee, add comments, search, status filtering, status transition, meaningful validation errors, meaningful backend/business errors | E (frontend prompt) |
| FR-3 | Uses the API contract as the source of truth. Invents no fields or endpoints | E (frontend prompt) |
| FR-4 | Only offers status changes the backend allows (`allowedTransitions`) | A-11, implemented |
| FR-5 | Search and filter state is kept in the URL (shareable, survives reload) | A (architecture §6.2), implemented |
| FR-6 | Default list shows all statuses, including closed and cancelled | A (API-2), implemented. Q-10 |

## 12. Testing requirements

| ID | Requirement | Status |
|----|-------------|--------|
| TR-1 | JUnit 5, Spring Boot Test, Mockito and Testcontainers where appropriate (TC-5) | E. D-5: JUnit Jupiter 6 is in use |
| TR-2 | Explicit tests for each allowed transition (`OPEN→IN_PROGRESS`, `IN_PROGRESS→RESOLVED`, `RESOLVED→CLOSED`, `OPEN→CANCELLED`, `IN_PROGRESS→CANCELLED`) | E (test-strategy prompt) |
| TR-3 | Explicit rejection tests including `CLOSED→OPEN`, `RESOLVED→OPEN`, `CANCELLED→OPEN` | E (test-strategy prompt) |
| TR-4 | State-machine integration tests pass | E (AC-14) |
| TR-5 | Tests for creation, retrieval, listing, update, assignee, comments, search, filtering, validation, not-found, valid and invalid transitions | E (test-generation prompt) |
| TR-6 | Critical end-to-end journeys, including persistence after restart | E (integration-test prompt) |
| TR-7 | Test pyramid, database tests on PostgreSQL, coverage floor | A ([`test-strategy.md`](test-strategy.md), `rules/testing.md`), implemented |

## 13. Security considerations

| ID | Consideration | Status |
|----|---------------|--------|
| SEC-1 | No secrets in the repository. Configuration from the environment | E (AC-15, Prompt 0) |
| SEC-2 | Validate all input at the backend (REQ-9). Bound SQL parameters only. User text rendered as text (no HTML injection) | E (REQ-9) + A (design), implemented |
| SEC-3 | Error responses never expose internals (stack traces, SQL, class names) | A (design), implemented |
| SEC-4 | **Authentication and authorisation:** none in v1. Anyone who can reach the system can do everything, and comment authors are unverified | A-4, implemented. **D-1** |
| SEC-5 | Deployment boundary (internal network / SSO proxy), request-size limits, security headers | D-1 (security review H-2, M-2, M-4) |
| SEC-6 | Dependency vulnerability scanning | A (`rules/security.md` §5). Not yet automated (security review M-1) |

## 14. Observability and logging

| ID | Consideration | Status |
|----|---------------|--------|
| OB-1 | Every request has a correlation id (accepted from `X-Correlation-Id` or generated), returned in the header and in every error body, and shown in the UI for server errors | A (architecture §18), implemented |
| OB-2 | Business events logged at INFO (ticket created, status changed, comment added) without ticket text | A, implemented |
| OB-3 | Never log secrets, full request bodies, ticket descriptions or comment text (may contain customer data) | A (`rules/security.md` §3), implemented |
| OB-4 | Structured (JSON) logs in production, metrics, alerting | Q-11 |

---

## 15. Assumptions register (for sign-off)

All **implemented** assumptions are covered by tests. Changing one means updating the spec, code and tests together.

| ID | Assumption | Status |
|----|-----------|--------|
| A-4 | No authentication or authorisation in v1 | Implemented. Needs D-1 |
| A-11 | Ticket responses include `allowedTransitions`. The UI shows only those actions | Implemented |
| A-13 | Assignee is free text, not a user account | Implemented |
| A-14 | Comment author is free text supplied by the client | Implemented |
| A-15 | Priorities `LOW`, `MEDIUM`, `HIGH`, `URGENT`. Default `MEDIUM` | Implemented |
| A-17 | Closed/cancelled tickets can't be edited or reassigned | Implemented |
| A-18 | Closed/cancelled tickets can't receive comments | Implemented |
| A-19 | No assignee required to start work. Assigning doesn't change status | Implemented |
| A-20 | No reopening (`RESOLVED → IN_PROGRESS` rejected) | Implemented |
| A-21 | Same-status transitions rejected | Implemented |
| A-22 | Lifecycle timestamps recorded | Implemented |
| A-24 | Search covers title and description (not comments or assignee), case-insensitive substring | Implemented |
| A-30 | Text is trimmed. Whitespace-only counts as blank | Implemented |
| A-34 / DM-6 | Comments don't change the ticket's version or `updatedAt` | Implemented |
| DM-1, DM-2, API-7 | No deletion of tickets or comments | Implemented |
| DM-3 | Description required | Implemented |
| DM-4 | Blank assignee = unassigned | Implemented |
| DM-5 | Length limits 200 / 5000 / 100 / 100 / 5000 / 100 | Implemented |
| DM-8 | Status filter accepts several values | Implemented |
| API-1 | A multi-word search is one phrase | Implemented |
| API-2 | Unfiltered list includes closed/cancelled tickets | Implemented |
| A-1 | Scale ≤ 100 k tickets, tens of users | Provisional (no target) |

## 16. Open questions (product owner)

| ID | Question |
|----|----------|
| Q-1 | Is there a customer/requester for each ticket (name, contact), separate from the assignee? |
| Q-2 | Should a resolved ticket be reopenable (e.g. `RESOLVED → IN_PROGRESS`)? REQ-11 as written says no. |
| Q-3 | May closed/cancelled tickets still be edited or commented on (A-17/A-18 say no)? |
| Q-4 | Must a ticket have an assignee before work starts? Should assigning start work automatically? |
| Q-5 | Should tickets or comments ever be deleted or archived? |
| Q-6 | Should adding a comment count as "activity" for sorting by last modified? |
| Q-7 | Is a description mandatory? |
| Q-8 | Are the four priority levels right? |
| Q-9 | Should assignee and comment author be real users (requires authentication)? |
| Q-10 | Should the default list hide closed/cancelled tickets? |
| Q-11 | What operational visibility is expected in production (structured logs, metrics, alerts)? |

## 17. Decisions needed

| ID | Decision | Before | Reference |
|----|----------|--------|-----------|
| D-1 | Authentication / deployment boundary (internal network, SSO proxy, or v1 authentication) | Any deployment | Security review H-2 |
| D-2 | API contract semantics for `null` vs missing fields and error aggregation (keep the current implementation or simplify) | Further API changes | Spec review SR-02/SR-03, plan STEP-02 |
| D-3 | Make `openapi.yaml` the single machine-readable contract | Frontend type generation, contract tests | Spec review SR-04, plan STEP-05 |
| D-4 | Measurable NFRs: latency target at a given data size, WCAG level, supported browsers | Performance and accessibility testing | Spec review SR-17 |
| D-5 | Accept JUnit Jupiter 6 (Spring Boot BOM) instead of "JUnit 5" (TC-5), and whether Mockito-based service tests are wanted | Final sign-off | Acceptance review (re-run) C-6 |
| D-6 | Sign-off of every assumption in §15 (confirm or change) | Final sign-off | Spec review SR-01 |

## Changelog

- 2026-09-25: Created from Prompt 0 (REQ-1…12, TC-1…5), recorded verbatim.
- 2026-09-26: Requirements analysis (milestone 02, guide Prompt 1): actors, use cases, NFRs, business, validation,
  error, persistence, API, frontend, testing, security and observability requirements. Assumptions register, open
  questions and decisions. Acceptance criteria AC-1…15 recorded. No REQ ids changed, and no behaviour changed.
