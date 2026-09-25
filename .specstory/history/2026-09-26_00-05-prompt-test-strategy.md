# 2026-09-26 00:05 — Test strategy

**Phase:** Test strategy

## Prompt

Review all specification documents.

Do NOT implement production code.

Create:

spec/test-strategy.md

Define the testing strategy for the application.

Cover:

1. Unit tests
2. Service tests
3. Controller tests
4. Repository tests
5. Integration tests
6. State-machine tests
7. Validation tests
8. API error tests
9. Search tests
10. Status filtering tests
11. Persistence/restart behavior
12. Frontend tests
13. End-to-end tests where appropriate

The state machine must have explicit tests for:

OPEN → IN_PROGRESS       allowed
IN_PROGRESS → RESOLVED   allowed
RESOLVED → CLOSED        allowed
OPEN → CANCELLED         allowed
IN_PROGRESS → CANCELLED  allowed

And explicit rejection tests for invalid transitions including:

CLOSED → OPEN
RESOLVED → OPEN
CANCELLED → OPEN

Define the test pyramid and explain which tests should catch which classes of defects.

Do not implement code.
