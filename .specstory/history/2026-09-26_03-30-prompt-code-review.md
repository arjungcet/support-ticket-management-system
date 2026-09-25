# 2026-09-26 03:30 — Strict code review

**Phase:** Review

## Prompt

Perform a strict senior-engineer code review of the entire implementation.

Review the code against:

- spec/requirements.md
- spec/architecture.md
- spec/data-model.md
- spec/api-contract.md
- spec/state-machine.md
- spec/test-strategy.md
- rules/java-springboot.md
- rules/testing.md
- rules/api-standards.md

Look for:

1. Incorrect business logic
2. State-machine violations
3. Missing validation
4. Incorrect HTTP status codes
5. API contract violations
6. Security issues
7. Transaction problems
8. Database issues
9. Race conditions
10. Poor exception handling
11. N+1 queries
12. Missing indexes
13. Weak test coverage
14. Over-mocking
15. Frontend/backend mismatches
16. Hard-coded configuration
17. Secrets
18. Dead code
19. Unnecessary complexity
20. Maintainability problems

For every finding provide:

Severity
File
Problem
Why it is a problem
Recommended fix

Do not modify code yet.
