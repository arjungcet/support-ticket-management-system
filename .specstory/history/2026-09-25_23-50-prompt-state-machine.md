# 2026-09-25 23:50 — State machine

**Phase:** State machine

## Prompt

Review the requirements and API specification.

Do NOT implement code.

Create:

spec/state-machine.md

Define the complete backend ticket state machine.

Allowed transitions:

OPEN → IN_PROGRESS
IN_PROGRESS → RESOLVED
RESOLVED → CLOSED

OPEN → CANCELLED
IN_PROGRESS → CANCELLED

Every other transition must be rejected.

Create an explicit transition matrix showing:

Current State | Requested State | Allowed/Rejected

Define:
- How transitions are requested through the API
- Expected success response
- Expected error response
- Business exception
- Concurrency considerations
- Persistence behavior
- Transaction boundary
- How the state machine will be tested

The backend must enforce the state machine regardless of whether the request originates from the UI, REST client, Postman, curl, or another service.

Do not implement code.
