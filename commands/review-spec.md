# Command: Review Spec

Review a specification-phase artifact for completeness, consistency and testability **before** it drives later phases.

**Usage**: `/review-spec <path>` — e.g. `spec/functional-spec.md`, `spec/api-contract.md`,
`spec/data-model.md`, `spec/state-machine.md`.

---

You are a senior architect reviewing a design document. Your job is to find what is **missing, ambiguous, contradictory
or untestable** — not to rewrite it.

## Inputs to load first

1. The document under review.
2. All earlier-phase artifacts it must be consistent with (see `rules/ai-assisted-development.md` §1 phase table),
   especially `spec/requirements.md`.
3. `skills/documentation/SKILL.md` (writing standards) and `rules/api-standards.md` (for contract docs).

## Checklist

**Coverage & traceability**
- Every requirement (REQ-1 … REQ-12 in `spec/requirements.md`) is addressed, or explicitly marked out of scope with a reason.
- IDs (REQ / AC / BR / ADR) are present and cross-references resolve.

**Precision**
- Every input field: type, required/optional, min/max length, allowed values, trimming/normalisation, default.
- Every operation: success response, and every error condition with status + error `code`.
- No vague words ("appropriate", "fast", "etc.", "user-friendly") without measurable definition.
- Acceptance criteria in Given/When/Then and each is testable.

**Ticket state machine**
- All states listed; initial state defined; terminal states (`CLOSED`, `CANCELLED`) defined.
- Full `from × to` matrix — every pair is explicitly allowed or rejected (incl. self-transitions).
- Rejection behaviour specified (status code, error code, message).
- Interactions defined: can title/description/priority/assignee be edited or comments added in terminal states?
  Is `RESOLVED → IN_PROGRESS` (reopen) allowed? (The requirement does not list it — must be decided, not assumed.)

**Search, filter, list**
- Which fields keyword search covers; case sensitivity; partial match; wildcard escaping; combination with status filter;
  default sort; pagination defaults and limits; empty-query behaviour.

**Data & concurrency**
- Identity strategy, timestamps, who/what an "assignee" is (free text vs user entity), comment author, optimistic locking.

**Consistency**
- Names, enums, field limits and status codes match across spec, data model, OpenAPI, and state-machine docs.

**Non-functional & security**
- Authentication/authorisation stance stated (even if "out of scope for v1").
- Error message content safe for end users.

## Output

```
## Verdict
Ready / Ready with minor changes / Not ready

## Issues
| # | Severity | Section | Issue | Why it matters | Suggested resolution |
|---|----------|---------|-------|----------------|----------------------|

## Open questions for the user
- Q-1 ...

## Assumptions detected (implicit in the doc, should be made explicit)
- ...
```

Do not edit the document unless the user asks; propose changes instead.
