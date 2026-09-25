# REST API Standards

Applies to: every HTTP endpoint exposed by the backend and every client call in the frontend.
Status: **binding**. The API contract (`spec/api-contract.md`, with its machine-readable form `spec/openapi.yaml`) is the
source of truth; code must match it, not the other way round.

---

## 1. General

- Base path: `/api/v1`. Breaking changes require a new version prefix.
- JSON only (`application/json`; errors `application/problem+json`). UTF-8.
- Field names `camelCase`. Enum values `UPPER_SNAKE_CASE` (e.g. `IN_PROGRESS`).
- Timestamps: ISO-8601 UTC strings (`2026-09-25T22:30:00Z`).
- IDs are opaque to clients; never expose DB internals beyond the identifier itself.
- Contract-first: update the OpenAPI spec (and get it reviewed with `commands/review-spec.md`) **before** changing
  an endpoint.

## 2. Resource naming & methods

- Plural nouns, no verbs in paths: `/tickets`, `/tickets/{id}`, `/tickets/{id}/comments`.
- Sub-resources only for true ownership (comments belong to a ticket).
- State changes that aren't plain field edits are modelled explicitly, e.g. `POST /tickets/{id}/status-transitions`
  or `PATCH /tickets/{id}/status` — the final choice is made in the API-contract phase and recorded in an ADR.

| Action | Method | Success | Notes |
|--------|--------|---------|-------|
| Create | `POST /tickets` | `201 Created` + `Location` header + body | |
| List / search / filter | `GET /tickets?status=&q=&page=&size=&sort=` | `200` | always paginated |
| Get one | `GET /tickets/{id}` | `200` | |
| Partial update | `PATCH /tickets/{id}` | `200` + updated body | title, description, priority |
| Replace sub-resource | `PUT /tickets/{id}/assignee` | `200` + updated ticket | assign / unassign |
| Add comment | `POST /tickets/{id}/comments` | `201` | |
| Delete (if ever added) | `DELETE` | `204` | |

- `PUT` = full replacement only. Prefer `PATCH` for partial updates; define its semantics explicitly
  (absent field = unchanged; explicit `null` = clear, only where the spec allows).
- `GET` is safe and idempotent; never mutate on `GET`.

## 3. Query parameters (list endpoints)

- Pagination: `page` (0-based), `size` (default 20, max 100). Values out of range → `400`.
- Sorting: `sort=field,asc|desc`, allow-listed fields only.
- Filtering: `status=OPEN` (repeatable for multiple values if the spec allows).
- Search: `q=<keyword>` — case-insensitive match; which fields are searched is defined in the spec.
- Paged response shape:

```json
{
  "content": [ { "...": "..." } ],
  "page": { "number": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
}
```

Do **not** serialise Spring's `Page`/`PageImpl` directly — map to a stable DTO.

## 4. Request / response DTOs

- Separate request and response types per use case (`CreateTicketRequest`, `UpdateTicketRequest`, `TicketResponse`,
  `TicketSummaryResponse`). Never bind requests directly to entities (mass-assignment risk).
- Server-controlled fields (`id`, `status` on create, `createdAt`, `updatedAt`, `version`) are ignored/rejected on input.
- Unknown JSON properties on input → `400` (`FAIL_ON_UNKNOWN_PROPERTIES=true`) so typos surface.
- Concurrency: responses include `version`; updates send it back (body field or `If-Match` ETag — decided in contract phase).
  Mismatch → `409`.

## 5. Errors — RFC 9457 Problem Details

All errors use `application/problem+json`:

```json
{
  "type": "https://supportdesk.example/problems/invalid-state-transition",
  "title": "Invalid status transition",
  "status": 409,
  "detail": "Cannot change status from CLOSED to OPEN.",
  "instance": "/api/v1/tickets/42/status-transitions",
  "code": "TICKET_INVALID_TRANSITION",
  "correlationId": "a1b2c3",
  "errors": [
    { "location": "body", "field": "title", "code": "BLANK", "message": "Title must not be blank." }
  ]
}
```

- `code`: stable, machine-readable, `UPPER_SNAKE_CASE`; the frontend maps on `code`, never on `detail` text.
- `errors[]`: always present (`[]` when empty). One entry per field problem, each with `location`, `field`, `code` and `message`.
  Submitted values are never echoed back. The full catalogue is in `spec/api-contract.md` §2.
- `detail`: human-readable, safe to show to end users; no internals.

| Situation | Status |
|-----------|--------|
| Malformed JSON / wrong JSON type (`MALFORMED_REQUEST`); any path, query or body validation failure (`VALIDATION_FAILED`) | `400` |
| Unauthenticated / unauthorised (when auth exists) | `401` / `403` |
| Resource not found | `404` |
| Invalid state transition, optimistic-lock conflict | `409` |
| Semantically invalid but well-formed (business rule) | `422` |
| Unexpected server error | `500` (generic message + correlationId) |

## 6. Frontend consumption

- A single typed API client module handles base URL, JSON, and problem parsing into a typed `ApiError`.
- UI shows `detail` (or a mapped friendly message by `code`) and highlights fields from `errors[]` next to inputs.
- Network failures and `5xx` show a generic retryable message; never render raw response bodies.
- Types for requests/responses are generated from `spec/openapi.yaml`, which is derived from `spec/api-contract.md`.

## 7. Documentation

- Human-readable contract: `spec/api-contract.md` (normative). Machine-readable: OpenAPI 3.1 in `spec/openapi.yaml`, kept
  in sync with it. springdoc may expose a runtime spec, but the checked-in files are canonical.
- Every endpoint documents: purpose, params, request/response schemas, all error statuses with `code`s, examples.
