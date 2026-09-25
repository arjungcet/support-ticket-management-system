# Support Ticket Management System — REST API Contract

| Status | Last updated | Related |
|--------|--------------|---------|
| Draft — awaiting review | 2026-09-25 | [`requirements.md`](requirements.md), [`architecture.md`](architecture.md) §8, §10, §14, [`data-model.md`](data-model.md), [`rules/api-standards.md`](../rules/api-standards.md), [ADR-0001](../docs/adr/0001-use-postgresql.md) |

This is the **normative API contract**. Backend controllers, frontend client types, MSW mocks and API tests are all
derived from it. A machine-readable OpenAPI 3.1 file (`spec/openapi.yaml`) will be produced from this document before
implementation starts. If the two ever disagree, this document wins until it is corrected.

> Decisions resting on unanswered product questions are marked **⚠ A-n** (architecture), **⚠ DM-n** (data model) or
> **⚠ API-n** (new here, §9). The functional spec may still change them. If it does, this contract is updated first.

---

## 1. Conventions

### 1.1 General

| Item | Rule |
|------|------|
| Base URL | `/api/v1`. All paths below are relative to it |
| Media type | Requests: `Content-Type: application/json`. Success responses: `application/json`. Errors: `application/problem+json` |
| Encoding | UTF-8 |
| Authentication | None in v1 (⚠ A-4) |
| Property names | `camelCase` |
| Enum values | `UPPER_SNAKE_CASE` strings |
| Timestamps | ISO-8601 UTC with `Z`, microsecond precision at most, e.g. `2026-09-25T22:30:00.123456Z` |
| IDs | JSON integers (int64), ≥ 1 |
| Nullable fields | Always **present** in responses with value `null`. Never omitted, so clients and tests can rely on the shape |
| Text input | All string inputs are **trimmed** before validation and storage. Length limits apply to the trimmed value (⚠ A-30) |
| Unknown request properties | Rejected: `400 VALIDATION_FAILED` with field code `UNKNOWN_FIELD` |
| Unknown query parameters | Ignored |
| Correlation | Clients may send `X-Correlation-Id` (≤ 64 chars `[A-Za-z0-9-]`). The server generates one otherwise, and **always** returns it in the `X-Correlation-Id` response header and in error bodies |

### 1.2 Concurrency (optimistic locking)

Every ticket representation carries `version` (int64). Requests that **modify a ticket** must send the `version` they
last read:

- `PATCH /tickets/{ticketId}`
- `PUT /tickets/{ticketId}/assignee`
- `POST /tickets/{ticketId}/status-transitions`

If it doesn't match the current version, the server returns `409 TICKET_CONCURRENT_MODIFICATION` and changes nothing.
A successful modification that actually changes something returns the new `version`. A request that changes nothing
(same values) returns `200` with `version` and `updatedAt` **unchanged**.

Adding a comment does **not** require or change `version` (⚠ A-34, DM-6).

### 1.3 Error precedence

When a request has several problems, the server reports **only the first** in this order. This ordering is deterministic
and tests rely on it:

1. `415 UNSUPPORTED_MEDIA_TYPE`: wrong `Content-Type` on a request with a body
2. `400 MALFORMED_REQUEST`: body is not parseable JSON, or a property has the wrong JSON type
3. `400 VALIDATION_FAILED`: path, query or body validation. **All** field errors are reported together in `errors[]`
4. `404 TICKET_NOT_FOUND`
5. `409 TICKET_CONCURRENT_MODIFICATION`: `version` mismatch
6. Business rules: `409 TICKET_INVALID_TRANSITION`, `422 TICKET_NOT_EDITABLE`, `422 TICKET_NOT_COMMENTABLE`

---

## 2. Error response structure

Every non-2xx response uses **RFC 9457 Problem Details** with project extensions. There is exactly one shape:

```json
{
  "type": "https://supportdesk.example/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains 2 invalid fields.",
  "instance": "/api/v1/tickets",
  "code": "VALIDATION_FAILED",
  "correlationId": "7f3c2a9e-51b4-4d0e-9a51-0c2f5e8d1b77",
  "timestamp": "2026-09-25T22:30:00.123456Z",
  "errors": [
    { "location": "body", "field": "title", "code": "BLANK", "message": "Title must not be blank." },
    { "location": "body", "field": "priority", "code": "INVALID_VALUE", "message": "Priority must be one of LOW, MEDIUM, HIGH, URGENT." }
  ]
}
```

| Property | Type | Always present | Description |
|----------|------|----------------|-------------|
| `type` | string (URI) | ✅ | `https://supportdesk.example/problems/{kebab-case-code}`. An identifier, not necessarily dereferenceable |
| `title` | string | ✅ | Short, fixed summary per `code` |
| `status` | integer | ✅ | Same as the HTTP status |
| `detail` | string | ✅ | Human-readable explanation specific to this occurrence. **Safe to show to end users.** Never contains stack traces, SQL, class names or internal ids other than the ticket id |
| `instance` | string | ✅ | Request path (without query string) |
| `code` | string | ✅ | **Stable machine-readable error code** (§2.1). Clients and tests branch on this, never on `title`/`detail` text |
| `correlationId` | string | ✅ | Same as the `X-Correlation-Id` response header. Shown by the UI for `5xx` |
| `timestamp` | string (date-time) | ✅ | When the error occurred |
| `errors` | array | ✅ (may be `[]`) | Field-level problems. Non-empty only for `VALIDATION_FAILED` |
| `errors[].location` | `"body"` \| `"query"` \| `"path"` | ✅ | Where the offending input was |
| `errors[].field` | string \| null | ✅ | JSON property or parameter name (`title`, `size`, `ticketId`). `null` for an object-level rule (e.g. "at least one field") |
| `errors[].code` | string | ✅ | Field error code (§2.2) |
| `errors[].message` | string | ✅ | Human-readable, safe to display next to the field |
| *business extensions* | varies | per `code` | e.g. `currentStatus`, `targetStatus`, `allowedTransitions` (§2.1) |

The submitted value is **never echoed back** (no `rejectedValue`). This avoids reflecting long or sensitive text and
keeps assertions stable.

### 2.1 Error code catalogue

| HTTP | `code` | `title` | When | Extra properties |
|------|--------|---------|------|------------------|
| 400 | `MALFORMED_REQUEST` | Malformed request | Body is not valid JSON, is not a JSON object, or a property has the wrong JSON type (e.g. `"title": 123`, `"version": "3"`) | — |
| 400 | `VALIDATION_FAILED` | Validation failed | Any path/query/body validation failure (§2.2) | `errors[]` non-empty |
| 404 | `TICKET_NOT_FOUND` | Ticket not found | No ticket with the given `ticketId` | `ticketId` |
| 404 | `RESOURCE_NOT_FOUND` | Resource not found | No such route | — |
| 405 | `METHOD_NOT_ALLOWED` | Method not allowed | Route exists, method doesn't. The `Allow` header lists the valid methods | — |
| 409 | `TICKET_CONCURRENT_MODIFICATION` | Ticket was modified | `version` mismatch (§1.2) | `ticketId`, `currentVersion` |
| 409 | `TICKET_INVALID_TRANSITION` | Invalid status transition | Target status not reachable from the current status (§6.9) | `ticketId`, `currentStatus`, `targetStatus`, `allowedTransitions` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Unsupported media type | Body sent with a `Content-Type` other than `application/json` | — |
| 422 | `TICKET_NOT_EDITABLE` | Ticket cannot be edited | Update or assign on a `CLOSED`/`CANCELLED` ticket (⚠ A-17) | `ticketId`, `currentStatus` |
| 422 | `TICKET_NOT_COMMENTABLE` | Ticket cannot be commented on | Comment on a `CLOSED`/`CANCELLED` ticket (⚠ A-18) | `ticketId`, `currentStatus` |
| 500 | `INTERNAL_ERROR` | Internal server error | Any unexpected failure. `detail` is always the generic "An unexpected error occurred. Please try again or contact support with the correlation id." | — |

**Status-code semantics:**
- **409** means the request conflicts with the ticket's *current state*: a stale version or an illegal lifecycle move.
  The UI refetches the ticket.
- **422** means a well-formed request that business rules forbid for this ticket.

### 2.2 Field error codes (`errors[].code`)

| Code | Meaning | Typical triggers |
|------|---------|------------------|
| `REQUIRED` | Missing or `null` where a value is required | `title` absent. `version` absent |
| `BLANK` | Empty or whitespace-only string | `"title": "   "` |
| `TOO_LONG` | Longer than the maximum after trimming | `title` > 200 chars |
| `INVALID_VALUE` | Not an allowed value: unknown enum, out-of-range number, bad format | `"priority": "CRITICAL"`, `size=0`, `ticketId=abc` |
| `UNKNOWN_FIELD` | Property not defined for this request | `"status"` in a create request |
| `NO_CHANGES_REQUESTED` | Object-level (`field: null`): request contains nothing to update | `PATCH` body with only `version` |

### 2.3 Field limits (from [`data-model.md`](data-model.md) §7, ⚠ DM-5)

| Field | Required | Min (after trim) | Max (after trim) |
|-------|----------|------------------|------------------|
| `title` | create: yes | 1 | 200 |
| `description` | create: yes (⚠ DM-3) | 1 | 5000 |
| `priority` | no (default `MEDIUM`) | enum | enum |
| `assignee` | no | 1 (blank ⇒ unassigned, ⚠ DM-4) | 100 |
| comment `author` | yes (⚠ A-14) | 1 | 100 |
| comment `body` | yes | 1 | 5000 |
| query `q` | no | 0 (empty ⇒ no filter) | 100 |

Lengths are measured in characters as described in [`data-model.md`](data-model.md) §7.

---

## 3. Enumerations

| Enum | Values |
|------|--------|
| `TicketStatus` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED` |
| `TicketPriority` | `LOW`, `MEDIUM`, `HIGH`, `URGENT` (⚠ A-15) |
| `TicketSortField` | `createdAt`, `updatedAt`, `priority`, `status` (⚠ A-25) |
| `SortDirection` | `asc`, `desc` |

Priority sort order is by rank `LOW < MEDIUM < HIGH < URGENT`. Status sort order follows the lifecycle order listed
above. Neither sorts alphabetically ([`data-model.md`](data-model.md) §13.2).

### 3.1 Allowed status transitions (⚠ A-20, A-21)

| Current status | `allowedTransitions` |
|----------------|----------------------|
| `OPEN` | `["IN_PROGRESS", "CANCELLED"]` |
| `IN_PROGRESS` | `["RESOLVED", "CANCELLED"]` |
| `RESOLVED` | `["CLOSED"]` |
| `CLOSED` | `[]` |
| `CANCELLED` | `[]` |

Arrays are always returned in the order shown. All other pairs, including self-transitions, are rejected with
`409 TICKET_INVALID_TRANSITION`.

---

## 4. Resource representations

### 4.1 `TicketResponse` (full ticket)

| Property | Type | Nullable | Notes |
|----------|------|----------|-------|
| `id` | int64 | no | |
| `title` | string | no | |
| `description` | string | no | |
| `priority` | `TicketPriority` | no | |
| `status` | `TicketStatus` | no | |
| `assignee` | string | **yes** | `null` = unassigned |
| `createdAt` | date-time | no | |
| `updatedAt` | date-time | no | |
| `resolvedAt` | date-time | **yes** | Set on transition to `RESOLVED` (⚠ A-22) |
| `closedAt` | date-time | **yes** | Set on transition to `CLOSED` |
| `cancelledAt` | date-time | **yes** | Set on transition to `CANCELLED` |
| `version` | int64 | no | Send back on modifying requests |
| `allowedTransitions` | `TicketStatus[]` | no | Per §3.1. Drives the UI's status buttons (⚠ A-11) |

```json
{
  "id": 42,
  "title": "Cannot log in to portal",
  "description": "User reports a 403 after password reset.",
  "priority": "HIGH",
  "status": "IN_PROGRESS",
  "assignee": "maria.lopez",
  "createdAt": "2026-09-25T09:00:00Z",
  "updatedAt": "2026-09-25T09:15:30.5Z",
  "resolvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "version": 2,
  "allowedTransitions": ["RESOLVED", "CANCELLED"]
}
```

### 4.2 `TicketSummaryResponse` (list item)

`id`, `title`, `priority`, `status`, `assignee` (nullable), `createdAt`, `updatedAt`. No `description`, no lifecycle
timestamps, no `version`, no `allowedTransitions`: list rows are for display and navigation only.

### 4.3 `CommentResponse`

| Property | Type | Nullable |
|----------|------|----------|
| `id` | int64 | no |
| `ticketId` | int64 | no |
| `author` | string | no |
| `body` | string | no |
| `createdAt` | date-time | no |

### 4.4 `PageResponse<T>`

```json
{
  "content": [ ],
  "page": { "number": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
}
```

- `content` holds items of type `T`. It is `[]` when the list is empty or the page is beyond the end.
- `number` is the 0-based page index as requested, and `size` is the requested size.
- `totalPages` is `0` when `totalElements` is `0`.

---

## 5. Endpoint overview

| # | Operation | Method & path | Success |
|---|-----------|---------------|---------|
| 1 | Create ticket | `POST /tickets` | `201` `TicketResponse` |
| 2 | List tickets | `GET /tickets` | `200` `PageResponse<TicketSummaryResponse>` |
| 3 | Get ticket details | `GET /tickets/{ticketId}` | `200` `TicketResponse` |
| 3a | List comments of a ticket | `GET /tickets/{ticketId}/comments` | `200` `PageResponse<CommentResponse>` |
| 4 | Update ticket (title, description, priority) | `PATCH /tickets/{ticketId}` | `200` `TicketResponse` |
| 5 | Update assignee | `PUT /tickets/{ticketId}/assignee` | `200` `TicketResponse` |
| 6 | Add comment | `POST /tickets/{ticketId}/comments` | `201` `CommentResponse` |
| 7 | Search tickets | `GET /tickets?q=…` (same endpoint as #2) | `200` |
| 8 | Filter tickets by status | `GET /tickets?status=…` (same endpoint as #2) | `200` |
| 9 | Change ticket status | `POST /tickets/{ticketId}/status-transitions` | `200` `TicketResponse` |

Endpoint 3a is not in the requested list, but the details view needs it to show comments (⚠ A-26).

**Why assignee has its own endpoint (resolves ⚠ A-29):** assigning or unassigning is a distinct user action.
Keeping it out of `PATCH` means every `PATCH` field is non-nullable. "Absent" always means "unchanged", and
"clear the assignee" is an explicit `null` on a dedicated resource. No tri-state JSON handling is needed.

### 5.1 Common path parameter

| Name | Type | Rules | Invalid → |
|------|------|-------|-----------|
| `ticketId` | int64 | Integer ≥ 1 | Not an integer, or < 1: `400 VALIDATION_FAILED` (`location: path`, `field: ticketId`, `INVALID_VALUE`). Valid but unknown: `404 TICKET_NOT_FOUND` |

---

## 6. Endpoints

### 6.1 Create ticket (REQ-1)

`POST /api/v1/tickets`

**Request body — `CreateTicketRequest`**

| Property | Type | Required | Rules |
|----------|------|----------|-------|
| `title` | string | ✅ | 1–200 after trim |
| `description` | string | ✅ | 1–5000 after trim |
| `priority` | `TicketPriority` | ❌ | Default `MEDIUM` |
| `assignee` | string \| null | ❌ | ≤ 100 after trim. Absent, `null` or blank ⇒ unassigned |

`id`, `status`, `version` and timestamps are **not accepted**. Sending them yields `UNKNOWN_FIELD`. A new ticket is
always `OPEN`.

```json
{ "title": "Cannot log in to portal", "description": "User reports a 403 after password reset.", "priority": "HIGH" }
```

**Responses**

| Status | Body | When |
|--------|------|------|
| `201 Created` | `TicketResponse` (`status: OPEN`, `version: 0`, `allowedTransitions: ["IN_PROGRESS","CANCELLED"]`, `createdAt == updatedAt`) | Success. Header `Location: /api/v1/tickets/{id}` |
| `400` | `MALFORMED_REQUEST` | Unparseable JSON / wrong JSON types |
| `400` | `VALIDATION_FAILED` | See below |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | |

**Validation errors**

| Input | `errors[]` entry |
|-------|------------------|
| `title` missing / `null` | `body`, `title`, `REQUIRED` |
| `title` blank | `body`, `title`, `BLANK` |
| `title` > 200 | `body`, `title`, `TOO_LONG` |
| `description` missing / `null` / blank / > 5000 | `body`, `description`, `REQUIRED` / `BLANK` / `TOO_LONG` |
| `priority` not an enum value | `body`, `priority`, `INVALID_VALUE` |
| `assignee` > 100 | `body`, `assignee`, `TOO_LONG` |
| `status` (or any other unknown property) present | `body`, `status`, `UNKNOWN_FIELD` |

**Business errors:** none. **Not found:** n/a. **State transitions:** n/a (initial state is always `OPEN`).

---

### 6.2 List tickets (REQ-2), with search (REQ-6) and filter (REQ-7)

`GET /api/v1/tickets`

**Query parameters**

| Name | Type | Default | Rules | Invalid → `400 VALIDATION_FAILED` (`location: query`) |
|------|------|---------|-------|------------------------------------------------------|
| `page` | int | `0` | ≥ 0 | `page`, `INVALID_VALUE` |
| `size` | int | `20` | 1–100 | `size`, `INVALID_VALUE` |
| `sort` | string | `createdAt,desc` | `{TicketSortField},{asc\|desc}`. Direction optional (default `asc`) | `sort`, `INVALID_VALUE` |
| `q` | string | — | Keyword. Trimmed. 0–100 chars. Empty ⇒ ignored | `q`, `TOO_LONG` |
| `status` | `TicketStatus`, repeatable | — (all statuses) | `status=OPEN&status=IN_PROGRESS` or `status=OPEN,IN_PROGRESS` (⚠ DM-8) | `status`, `INVALID_VALUE` |

- Results are always ordered by `sort` and then by `id` in the same direction, so pagination is stable.
- `q` and `status` combine with **AND**. Multiple `status` values combine with **OR**.
- Requesting a page beyond the end returns `200` with `content: []` and the correct totals.

**Response `200`:** `PageResponse<TicketSummaryResponse>`

```json
{
  "content": [
    { "id": 42, "title": "Cannot log in to portal", "priority": "HIGH", "status": "IN_PROGRESS",
      "assignee": "maria.lopez", "createdAt": "2026-09-25T09:00:00Z", "updatedAt": "2026-09-25T09:15:30.5Z" }
  ],
  "page": { "number": 0, "size": 20, "totalElements": 1, "totalPages": 1 }
}
```

**Errors:** `400 VALIDATION_FAILED` only. **Not found:** n/a. An empty result is `200` with `content: []`, never `404`.
**Business/state errors:** none.

#### 6.2.1 Search tickets (REQ-6): `GET /api/v1/tickets?q={keyword}`

| Aspect | Contract |
|--------|----------|
| Fields searched | `title`, `description` (⚠ A-24: not comments, assignee or id) |
| Matching | Case-insensitive **substring** (`q=LOGIN` matches "Cannot log**in** to portal"). Not word-based, no stemming, no relevance ranking |
| Special characters | `%`, `_`, `\` and quotes match **literally**. `q=100%` matches only text containing "100%" |
| Multiple words | Treated as **one phrase**: `q=log in` matches the substring "log in" only (⚠ API-1) |
| Empty / whitespace `q` | Ignored, same as no `q` |
| Combination | Works together with `status`, `sort`, `page`, `size` |

Example: `GET /api/v1/tickets?q=portal&status=OPEN&status=IN_PROGRESS&sort=priority,desc&page=0&size=20`

#### 6.2.2 Filter tickets by status (REQ-7): `GET /api/v1/tickets?status={status}`

| Aspect | Contract |
|--------|----------|
| Values | Any `TicketStatus`, case-sensitive (`open` → `400 INVALID_VALUE`) |
| Multiple | Repeat the parameter or comma-separate. Duplicates are ignored |
| Absent | All statuses, including terminal ones (⚠ API-2) |
| Unknown value | `400 VALIDATION_FAILED`, `query`, `status`, `INVALID_VALUE`, message lists the allowed values |

---

### 6.3 Get ticket details (REQ-3)

`GET /api/v1/tickets/{ticketId}`

| Status | Body | When |
|--------|------|------|
| `200` | `TicketResponse` | Found |
| `400` | `VALIDATION_FAILED` (`path`, `ticketId`, `INVALID_VALUE`) | Invalid id |
| `404` | `TICKET_NOT_FOUND` (`ticketId` extension) | Unknown id |

Comments are **not embedded**. Fetch them with §6.3a.

### 6.3a List comments of a ticket (supports REQ-3, REQ-5)

`GET /api/v1/tickets/{ticketId}/comments`

| Query param | Default | Rules |
|-------------|---------|-------|
| `page` | `0` | ≥ 0 |
| `size` | `50` | 1–100 |

Always sorted by `createdAt asc, id asc` (oldest first, conversation order). `sort` is not supported and is ignored.

| Status | Body | When |
|--------|------|------|
| `200` | `PageResponse<CommentResponse>` (may be empty) | Ticket exists |
| `400` | `VALIDATION_FAILED` | Invalid id / paging |
| `404` | `TICKET_NOT_FOUND` | Unknown ticket |

---

### 6.4 Update ticket (REQ-4: title, description, priority)

`PATCH /api/v1/tickets/{ticketId}`

**Request body — `UpdateTicketRequest`**

| Property | Type | Required | Rules |
|----------|------|----------|-------|
| `version` | int64 | ✅ | ≥ 0 |
| `title` | string | ❌ | If present: non-null, 1–200 after trim |
| `description` | string | ❌ | If present: non-null, 1–5000 after trim |
| `priority` | `TicketPriority` | ❌ | If present: non-null, valid enum |

At least one of `title`, `description` or `priority` must be present. Absent means unchanged. `null` is **not**
allowed for any of them. `assignee` and `status` are **unknown fields** here: use §6.5 and §6.9.

```json
{ "version": 2, "title": "Cannot log in to customer portal", "priority": "URGENT" }
```

**Responses**

| Status | Body | When |
|--------|------|------|
| `200` | `TicketResponse` | Updated, or nothing changed (§1.2) |
| `400` | `MALFORMED_REQUEST` / `VALIDATION_FAILED` | See below |
| `404` | `TICKET_NOT_FOUND` | Unknown id |
| `409` | `TICKET_CONCURRENT_MODIFICATION` | `version` mismatch |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | |
| `422` | `TICKET_NOT_EDITABLE` | Ticket is `CLOSED` or `CANCELLED` (⚠ A-17). Editing is allowed in `OPEN`, `IN_PROGRESS` and `RESOLVED` |

**Validation errors**

| Input | `errors[]` entry |
|-------|------------------|
| `version` missing / `null` | `body`, `version`, `REQUIRED` |
| `version` < 0 | `body`, `version`, `INVALID_VALUE` |
| `title` `null` / blank / > 200 | `body`, `title`, `REQUIRED` / `BLANK` / `TOO_LONG` |
| `description` `null` / blank / > 5000 | `body`, `description`, `REQUIRED` / `BLANK` / `TOO_LONG` |
| `priority` `null` / unknown | `body`, `priority`, `REQUIRED` / `INVALID_VALUE` |
| none of the three present | `body`, `null`, `NO_CHANGES_REQUESTED` |
| `assignee` / `status` / other | `body`, `<name>`, `UNKNOWN_FIELD` |

**Invalid state transition:** n/a. This endpoint never changes status.

---

### 6.5 Update assignee (REQ-4: assignee)

`PUT /api/v1/tickets/{ticketId}/assignee`

**Request body — `AssignTicketRequest`**

| Property | Type | Required | Rules |
|----------|------|----------|-------|
| `version` | int64 | ✅ | ≥ 0 |
| `assignee` | string \| null | ✅ key present (⚠ API-3) | `null`, empty or blank ⇒ **unassign**. Otherwise ≤ 100 after trim |

```json
{ "version": 3, "assignee": "maria.lopez" }
```
```json
{ "version": 4, "assignee": null }
```

**Responses**

| Status | Body | When |
|--------|------|------|
| `200` | `TicketResponse` | Assigned, unassigned, or unchanged (same value) |
| `400` | `MALFORMED_REQUEST` / `VALIDATION_FAILED` | `version` missing (`REQUIRED`) or < 0 (`INVALID_VALUE`). `assignee` key missing (`REQUIRED`). `assignee` > 100 (`TOO_LONG`). Unknown property (`UNKNOWN_FIELD`) |
| `404` | `TICKET_NOT_FOUND` | |
| `409` | `TICKET_CONCURRENT_MODIFICATION` | |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | |
| `422` | `TICKET_NOT_EDITABLE` | Ticket is `CLOSED` or `CANCELLED` (⚠ A-17) |

Assigning does **not** change status. There is no automatic `OPEN → IN_PROGRESS` (⚠ A-19).
**Invalid state transition:** n/a.

---

### 6.6 Add comment (REQ-5)

`POST /api/v1/tickets/{ticketId}/comments`

**Request body — `AddCommentRequest`**

| Property | Type | Required | Rules |
|----------|------|----------|-------|
| `author` | string | ✅ | 1–100 after trim (⚠ A-14: free text, no auth) |
| `body` | string | ✅ | 1–5000 after trim |

```json
{ "author": "maria.lopez", "body": "Reset the account lock; asked user to retry." }
```

**Responses**

| Status | Body | When |
|--------|------|------|
| `201 Created` | `CommentResponse` | Header `Location: /api/v1/tickets/{ticketId}/comments/{commentId}` (⚠ API-4) |
| `400` | `MALFORMED_REQUEST` / `VALIDATION_FAILED` | `author` / `body`: `REQUIRED`, `BLANK`, `TOO_LONG`. Unknown property: `UNKNOWN_FIELD` |
| `404` | `TICKET_NOT_FOUND` | |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | |
| `422` | `TICKET_NOT_COMMENTABLE` | Ticket is `CLOSED` or `CANCELLED` (⚠ A-18). Allowed in `OPEN`, `IN_PROGRESS` and `RESOLVED` |

No `version` is required, and the ticket's `version` and `updatedAt` are **not** changed (§1.2).
**Invalid state transition:** n/a.

---

### 6.7 Search tickets (REQ-6)

Provided by **§6.2** with the `q` parameter. See §6.2.1 for matching rules. There is no separate endpoint.

### 6.8 Filter tickets by status (REQ-7)

Provided by **§6.2** with the `status` parameter. See §6.2.2. There is no separate endpoint.

---

### 6.9 Change ticket status (REQ-11, REQ-12)

`POST /api/v1/tickets/{ticketId}/status-transitions`

This models a status change as a **command** applied to the ticket. It returns `200` with the updated ticket, not
`201`, because transitions are not stored as their own resource in v1 (⚠ A-23).

**Request body — `ChangeStatusRequest`**

| Property | Type | Required | Rules |
|----------|------|----------|-------|
| `version` | int64 | ✅ | ≥ 0 |
| `targetStatus` | `TicketStatus` | ✅ | Valid enum value |

```json
{ "version": 2, "targetStatus": "RESOLVED" }
```

**Responses**

| Status | Body | When |
|--------|------|------|
| `200` | `TicketResponse` with the new `status`, incremented `version`, new `updatedAt`, the matching lifecycle timestamp set, and new `allowedTransitions` | Legal transition |
| `400` | `MALFORMED_REQUEST` / `VALIDATION_FAILED` | `version` missing/negative. `targetStatus` missing (`REQUIRED`) or not an enum value (`INVALID_VALUE`). Unknown property |
| `404` | `TICKET_NOT_FOUND` | |
| `409` | `TICKET_CONCURRENT_MODIFICATION` | `version` mismatch. Checked **before** transition legality |
| `409` | `TICKET_INVALID_TRANSITION` | `targetStatus` not in the current status's `allowedTransitions` (§3.1), including self-transitions and every transition out of `CLOSED` or `CANCELLED` |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | |

**Invalid-transition response example** (`CLOSED → OPEN`):

```json
{
  "type": "https://supportdesk.example/problems/ticket-invalid-transition",
  "title": "Invalid status transition",
  "status": 409,
  "detail": "Ticket 42 cannot move from CLOSED to OPEN.",
  "instance": "/api/v1/tickets/42/status-transitions",
  "code": "TICKET_INVALID_TRANSITION",
  "correlationId": "0b6c1e0a-2f7d-4a63-8c9e-3d1b2a4f5e60",
  "timestamp": "2026-09-25T22:31:00Z",
  "errors": [],
  "ticketId": 42,
  "currentStatus": "CLOSED",
  "targetStatus": "OPEN",
  "allowedTransitions": []
}
```

Nothing is changed on rejection: `status`, `version` and timestamps stay the same.

**Lifecycle timestamps set on success:** `→ RESOLVED` sets `resolvedAt`. `→ CLOSED` sets `closedAt` and keeps
`resolvedAt`. `→ CANCELLED` sets `cancelledAt`.

---

## 7. Status-code matrix (per endpoint)

✅ = possible response. Useful as a checklist for API tests.

| Endpoint | 200 | 201 | 400 MALFORMED | 400 VALIDATION | 404 | 409 CONCURRENT | 409 TRANSITION | 415 | 422 NOT_EDITABLE | 422 NOT_COMMENTABLE |
|----------|-----|-----|---------------|----------------|-----|----------------|----------------|-----|------------------|---------------------|
| `POST /tickets` | | ✅ | ✅ | ✅ | | | | ✅ | | |
| `GET /tickets` | ✅ | | | ✅ | | | | | | |
| `GET /tickets/{id}` | ✅ | | | ✅ | ✅ | | | | | |
| `GET /tickets/{id}/comments` | ✅ | | | ✅ | ✅ | | | | | |
| `PATCH /tickets/{id}` | ✅ | | ✅ | ✅ | ✅ | ✅ | | ✅ | ✅ | |
| `PUT /tickets/{id}/assignee` | ✅ | | ✅ | ✅ | ✅ | ✅ | | ✅ | ✅ | |
| `POST /tickets/{id}/comments` | | ✅ | ✅ | ✅ | ✅ | | | ✅ | | ✅ |
| `POST /tickets/{id}/status-transitions` | ✅ | | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | | |

Every endpoint can also return `500 INTERNAL_ERROR`. Wrong methods return `405`, and unknown paths return `404 RESOURCE_NOT_FOUND`.

---

## 8. Guidance for consumers

### 8.1 Frontend

- Generate TypeScript types from `spec/openapi.yaml` once it exists. Until then, this document is the reference.
- Parse every non-2xx response with `Content-Type: application/problem+json` into a single `ApiError` type. Branch
  on `code`.
- `VALIDATION_FAILED`: map `errors[]` with `location: "body"` onto form fields by `field`. Show entries with
  `field: null` as a form-level message.
- `TICKET_CONCURRENT_MODIFICATION`: offer "Reload". Keep the user's unsaved input.
- `TICKET_INVALID_TRANSITION`: show `detail`, refetch the ticket, and re-render the status buttons from
  `allowedTransitions`.
- `TICKET_NOT_EDITABLE` / `TICKET_NOT_COMMENTABLE`: show `detail`. The UI should already hide these actions for
  terminal tickets.
- `5xx` / network: generic retryable message with `correlationId`.
- Keep list state in the URL using the same parameter names (`q`, `status`, `page`, `size`, `sort`).

### 8.2 Automated tests

- Assert `status`, `Content-Type`, `code`, and for validation errors the **set** of `(location, field, code)` tuples.
  Do not assert on `detail`, `title` or `message` text, `correlationId` values, or `timestamp`.
- Cover every ✅ in the §7 matrix at least once, and the full 5×5 transition matrix from §3.1 through §6.9.
- Error precedence (§1.3) is part of the contract. Include cases that combine two faults, e.g. an unknown id together
  with an invalid body → `400`, not `404`.
- Nullable fields are always present. Assert `null` explicitly rather than absence.

---

## 9. Assumptions and decisions

Resolved here (from `architecture.md` §20):

| ID | Resolution |
|----|------------|
| A-11 | `allowedTransitions` included in `TicketResponse` |
| A-23 | `POST /tickets/{ticketId}/status-transitions` → `200 TicketResponse` |
| A-26 | Comments via separate paginated `GET /tickets/{ticketId}/comments` (default size 50, oldest first) |
| A-27 | `version` in request body (no `ETag`/`If-Match`) |
| A-29 | Superseded: assignee moved to its own endpoint, so no tri-state PATCH is needed |

Still provisional (product decisions carried into this contract): A-14, A-15, A-17, A-18, A-19, A-20, A-21, A-24,
A-25, A-30, DM-3, DM-4, DM-5, DM-8.

New:

| ID | Assumption | Default chosen | Resolve in |
|----|-----------|----------------|------------|
| API-1 | A multi-word `q` is matched as one phrase, not as independent words | Phrase | **Spec** |
| API-2 | With no `status` filter, the list includes `CLOSED` and `CANCELLED` tickets | Include all | **Spec** (UI may default its filter to active statuses) |
| API-3 | `PUT /assignee` requires the `assignee` key to be present, and `null` means unassign | Key required | API contract (this doc) |
| API-4 | The comment `Location` header points to `/tickets/{id}/comments/{commentId}`, although no `GET` for a single comment exists in v1 | Header included for REST consistency | API contract. Alternatively omit the header |
| API-5 | Validation errors use `location`/`field`/`code`/`message`. `rejectedValue` is dropped (updates `rules/api-standards.md` §5) | As specified | API contract (this doc) |
| API-6 | All input errors (path, query, body) share `VALIDATION_FAILED`. The previously planned `INVALID_PARAMETER` code is dropped (updates `architecture.md` §14.2) | As specified | API contract (this doc) |
| API-7 | No `DELETE` endpoints for tickets or comments in v1 (DM-1, DM-2) | None | **Spec** |

## Changelog

- 2026-09-25 — Initial draft.
