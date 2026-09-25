# Testing Standards

Applies to: backend and frontend.
Status: **binding**. Detailed per-feature test cases live in the Test Strategy document
(`spec/test-strategy.md`, created in that phase); this file sets the rules.

---

## 1. Principles

- **Tests are derived from the specification**, not from the implementation. Every acceptance criterion
  in `spec/` maps to at least one test (traceability: reference the requirement id, e.g. `// REQ-7`).
- Test behaviour through public APIs, not private methods or internal state.
- A bug fix starts with a failing test that reproduces it.
- Tests are deterministic: no reliance on wall-clock time (inject `Clock`), random order, network, or shared state
  between tests.
- A test that has never been seen failing is not trusted — confirm new tests fail when the behaviour is broken.

## 2. Test pyramid (backend)

| Level | Scope | Tools | Location / naming |
|-------|-------|-------|-------------------|
| Unit | Domain logic (state machine, validation rules), services with mocked collaborators | JUnit 5, AssertJ, Mockito | `src/test/java`, `*Test.java` |
| Web slice | Controller ↔ JSON, validation, error mapping | `@WebMvcTest`, MockMvc, `@MockitoBean` | `*ControllerTest.java` |
| Persistence slice | Repositories, queries, search, constraints, migrations | `@DataJpaTest` + **Testcontainers PostgreSQL** | `src/integrationTest/java`, `*RepositoryIT.java` |
| Integration | Full HTTP → DB flows | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + Testcontainers | `src/integrationTest/java`, `*IT.java` |

- Unit and slice tests (`*Test`) live in `src/test/java` and run with `./gradlew test` (no Docker required).
- Integration tests (`*IT`) live in `src/integrationTest/java` (Gradle JVM Test Suite) and run with
  `./gradlew integrationTest`; `./gradlew check` / `build` runs both.
- Persistence-slice tests that need Testcontainers are `*RepositoryIT` and therefore also live in `integrationTest`.
- Mockito only at boundaries you own (repositories, clocks, other services). **Don't mock** domain objects,
  value objects, or the class under test. Don't mock JPA to test queries — use a real DB.

## 3. Mandatory coverage areas

- **State machine**: exhaustive table test (`@ParameterizedTest` / `@EnumSource` / `@MethodSource`) covering
  **every** `(from, to)` pair — allowed pairs succeed, all others are rejected (including self-transitions
  and every transition out of terminal states `CLOSED`, `CANCELLED`).
- **Validation**: each constraint on each request DTO — boundary values (empty, blank, max length, max+1, null).
- **Error contract**: each Problem Details status/`code` in `rules/api-standards.md` §5 is asserted at least once
  at the web layer (status, content type, `code`, `errors[]`).
- **Search & filter**: case-insensitivity, partial matches, no matches, combined with status filter, pagination edges,
  special characters (`%`, `_`, `'`) treated literally.
- **Concurrency**: optimistic-lock conflict returns `409`.
- **Persistence**: Flyway migrations apply cleanly on PostgreSQL; DB constraints reject invalid rows.

Coverage target: ≥ 80 % line / ≥ 70 % branch on `domain` and `application` (JaCoCo). Coverage is a floor,
not a goal — assertions must be meaningful.

## 4. H2 vs PostgreSQL

- H2 is **allowed** for: `@WebMvcTest`-free lightweight local runs, and fast smoke tests that don't exercise
  dialect-specific SQL.
- H2 is **not allowed** for: repository tests, search queries, migration tests, or anything asserting DB behaviour.
  Those use Testcontainers PostgreSQL (same major version as production).
- If Docker is unavailable in an environment, IT tests are skipped with a clear message — never silently switched to H2.

## 5. Style

- Structure: **Given / When / Then** (or Arrange / Act / Assert) with blank lines between sections.
- Naming: `methodOrBehaviour_condition_expectedResult` or `@DisplayName("rejects transition from CLOSED to OPEN")`.
- Assertions with **AssertJ**; one logical behaviour per test.
- Test data via small builders/factories (`TicketFixtures.anOpenTicket()`), not copy-pasted setup.
- No `Thread.sleep`. Use Awaitility if async behaviour ever appears.
- Each IT cleans up its own data (transaction rollback or truncation) — no order dependencies.

## 6. Frontend

- Unit/component: **Vitest or Jest + React Testing Library**. Query by role/label, not test ids where avoidable.
- API calls mocked at the network layer with **MSW**, using payloads that match `spec/api-contract.md` / `spec/openapi.yaml`
  (including Problem Details error bodies).
- Must cover: form validation messages, rendering of backend `errors[]` next to fields, generic error for `5xx`/network,
  empty states, loading states, status filter and search interaction.
- E2E (optional, later phase): Playwright against the real backend for the main happy path and one error path.

## 7. Definition of done (testing)

- `./gradlew build` and frontend `test` + `lint` + `typecheck` pass locally and in CI.
- New behaviour has tests at the lowest sensible level plus at least one test at the API boundary.
- No `@Disabled` / `.skip` without a linked issue and reason.
