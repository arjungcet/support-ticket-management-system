# Command: Generate Tests

Generate tests from the specification for a given feature, class, or plan step.

**Usage**: `/generate-tests <target>` — a spec section (`AC-4.*`), a plan step (`STEP-7`), or a class/path.

---

You are writing tests for the Support Ticket Management System. Tests are derived from the **spec**, not from the
current implementation — if the implementation disagrees with the spec, the test should fail and you report it.

## Inputs to load first

1. Relevant spec sections, acceptance criteria, business rules (`spec/`), API contract (`spec/api-contract.md`, `spec/openapi.yaml`),
   state machine (`spec/state-machine.md`).
2. `spec/test-strategy.md` for the planned cases at each level.
3. `rules/testing.md` (binding), plus `rules/api-standards.md` §5 for error assertions.
4. The code under test and existing tests/fixtures (reuse fixtures; don't duplicate).

## Steps

1. **List test cases first** as a table — `AC/BR id | level | scenario | expected` — covering happy path, every
   validation boundary, every error code, and edge cases. Show it before writing code if the list is long or ambiguous.
2. Choose the **lowest sufficient level** per case (`rules/testing.md` §2):
   domain unit → service unit (Mockito) → `@WebMvcTest` → `@DataJpaTest` + Testcontainers → `@SpringBootTest` IT.
   Frontend: RTL + MSW.
3. Write tests:
   - Given/When/Then structure, AssertJ, descriptive names or `@DisplayName`, reference the AC/BR id in a comment.
   - Parameterised tests for tables (state-machine matrix, validation boundaries).
   - Fixtures/builders for data; injected `Clock` for time.
   - Assert Problem Details: status, `application/problem+json`, `code`, and `errors[].field` where applicable.
4. **Run them.** `./gradlew test` / `./gradlew integrationTest` (or frontend `npm test`).
   - New tests for existing behaviour should pass; tests for not-yet-implemented behaviour should fail for the
     *expected* reason — confirm the failure message.
   - Sanity check: temporarily break the behaviour (or reason precisely about it) to confirm the test would catch it.
5. Never weaken an assertion or change expected values just to make a test pass. If the code seems wrong, report it.

## Output

- The test files.
- A short report: cases added (mapped to AC/BR ids), commands run and results, any spec gaps or implementation
  mismatches found, and any cases intentionally deferred with reason.
