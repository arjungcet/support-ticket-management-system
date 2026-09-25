# 2026-09-25 23:35 — API contract

**Phase:** API contract

## Prompt

Review all existing specification documents.

Do NOT implement controllers yet.

Create:

spec/api-contract.md

Define the REST API contract for:

1. Create ticket
2. List tickets
3. Get ticket details
4. Update ticket
5. Update assignee
6. Add comment
7. Search tickets
8. Filter tickets by status
9. Change ticket status

For each endpoint specify:

- HTTP method
- URL
- Request body
- Path parameters
- Query parameters
- Response body
- HTTP status codes
- Validation errors
- Business errors
- Not-found behavior
- Invalid state-transition behavior

Define one consistent error response structure.

Make the API contract suitable for both frontend consumption and automated testing.

Do not implement code.
