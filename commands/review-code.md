# Command: Review Code

Review code changes against the specification and project rules.

**Usage**: `/review-code [scope]` — scope is a path, a diff range (e.g. `main...HEAD`), or omitted for uncommitted changes.

---

You are reviewing code in the Support Ticket Management System as a senior engineer. You did not write this code;
treat it as coming from an untrusted contributor (including if it was AI-generated).

## Inputs to load first

1. The changes in scope (`git diff` for the given range, or the given paths).
2. Relevant spec items in `spec/`, `spec/api-contract.md`, `spec/state-machine.md`,
   and the current step in `spec/implementation-plan.md`.
3. Rules: `rules/java-springboot.md`, `rules/api-standards.md`, `rules/testing.md`, `rules/security.md`,
   `rules/ai-assisted-development.md` §3–4.

## Check, in priority order

1. **Correctness vs spec** — does the behaviour match the acceptance criteria exactly? Missing cases? Invented behaviour?
2. **State machine** — are all invalid transitions rejected in the backend domain layer? Terminal states enforced?
3. **Security** — secrets, SQL built from strings, unescaped `LIKE` wildcards, mass assignment, leaked internals in
   errors, `dangerouslySetInnerHTML`, permissive CORS.
4. **Data integrity & transactions** — `@Transactional` on the right layer, read-only where applicable, optimistic
   locking, DB constraints matching validation, Flyway migration correctness and immutability, N+1 queries,
   unbounded queries.
5. **Error handling** — Problem Details shape and codes per `rules/api-standards.md` §5; correct HTTP status;
   no swallowed exceptions.
6. **Layering** — dependency direction, entities not leaking out of `application`, thin controllers.
7. **Tests** — spec coverage, meaningful assertions, correct level (unit / slice / IT), Testcontainers for DB behaviour,
   exhaustive state-machine table.
8. **Frontend** — error display from `errors[]`/`code`, loading/empty states, types match the contract.
9. **Maintainability** — naming, duplication, dead code, Java 21 idioms.
10. **AI-verification checklist** — `rules/ai-assisted-development.md` §3; flag any API/dependency you cannot confirm exists.

**Verify before reporting**: read the surrounding code and, where possible, run `./gradlew build` / frontend tests.
Drop findings you can't substantiate.

## Output

Write to `docs/reviews/YYYY-MM-DD-<scope>.md` and summarise in chat:

```
## Summary
<2–3 sentences; overall verdict: approve / approve with changes / request changes>

## Findings
| # | Severity | Location | Problem | Failure scenario | Suggested fix |
|---|----------|----------|---------|------------------|---------------|
| 1 | blocker  | path/File.java:42 | ... | Given X, Y happens | ... |

## Tests run
<commands and results, or "not run" with reason>

## Spec gaps
<anything the spec doesn't answer that the code had to guess>
```

Severities: `blocker` (wrong/unsafe, must fix), `major` (should fix before merge), `minor`, `nit` (never blocks).
Do **not** modify code during review.
