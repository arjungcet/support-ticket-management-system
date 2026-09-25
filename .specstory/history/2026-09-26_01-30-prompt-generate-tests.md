# 2026-09-26 01:30 — Generate missing automated tests

**Phase:** Testing

## Prompt

Review the implementation against:

spec/test-strategy.md
spec/state-machine.md
spec/api-contract.md

Generate missing automated tests.

Prioritize behavior over implementation details.

Ensure there are explicit tests for:

- Ticket creation
- Ticket retrieval
- Ticket listing
- Ticket update
- Assignee update
- Comments
- Search
- Status filtering
- Backend validation
- Not-found errors
- Invalid status transitions
- Valid status transitions

For the state machine, test every allowed and representative invalid transition.

Do not modify production behavior just to make tests pass.

After generating tests, run them and report failures separately from implementation defects.
