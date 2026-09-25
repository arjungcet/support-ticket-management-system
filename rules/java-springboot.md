# Java 21 & Spring Boot Rules

Applies to: all backend code under `backend/`.
Status: **binding**. Deviations need a note in the PR description and, if architectural, an ADR (`docs/adr/`).

---

## 1. Java 21 conventions

- Target **Java 21 (LTS)**. Use modern language features where they improve clarity:
  - `record` for DTOs, commands, query objects, and value objects.
  - `sealed` interfaces + pattern-matching `switch` for closed hierarchies (e.g. domain results/errors).
  - Switch expressions over `if/else` chains on enums; exhaustive switches must not have a `default` branch
    so the compiler flags new enum constants.
  - `var` only when the type is obvious from the right-hand side.
  - Text blocks for multi-line SQL/JSON in tests.
- Immutability by default: `final` fields, no setters on DTOs/records, defensive copies (`List.copyOf`) for collections.
- **No `null` returns** from public methods. Use `Optional<T>` for "may be absent" lookups; never use `Optional`
  as a field or parameter type.
- Time: use `java.time.Instant` (UTC) for timestamps, injected `Clock` for testability. Never `new Date()` or
  `LocalDateTime.now()` without a clock.
- Naming: `PascalCase` types, `camelCase` members, `UPPER_SNAKE_CASE` constants, packages all lowercase.
  Name classes after their role: `TicketController`, `TicketService`, `TicketRepository`, `CreateTicketRequest`,
  `TicketResponse`, `TicketNotFoundException`.
- Methods small and single-purpose (guideline: < 30 lines). No boolean flag parameters that switch behaviour —
  split the method.
- Logging via SLF4J (`private static final Logger log = LoggerFactory.getLogger(...)`) with parameterised
  messages (`log.info("Ticket {} created", id)`). Never log secrets, full request bodies, or PII.
- No wildcard imports. No unused code, commented-out code, or `System.out`.
- Lombok: **not used** by default (records cover most needs). If introduced later, restrict to
  `@Getter`, `@RequiredArgsConstructor`; never `@Data` on JPA entities.

## 2. Spring Boot architecture

### 2.1 Package layout — package by feature, layered inside

```
com.supportdesk
├── SupportDeskApplication.java
├── ticket/
│   ├── api/            # @RestController, request/response DTOs, API mappers
│   ├── application/    # @Service use cases, commands, transaction boundaries
│   ├── domain/         # entities, TicketStatus + transition rules, domain exceptions
│   └── persistence/    # Spring Data repositories, JPA Specifications / queries
└── shared/
    ├── error/          # @RestControllerAdvice, ProblemDetail mapping, base exceptions
    └── config/         # Spring configuration (Clock, CORS, Jackson, OpenAPI)
```

Comments may live inside `ticket/` (they are owned by a ticket) unless the spec makes them an independent feature.

### 2.2 Layering and dependency direction

```
api  ──►  application  ──►  domain  ◄──  persistence
```

| Layer | May depend on | Must NOT depend on |
|-------|---------------|--------------------|
| `api` | `application`, `domain` types for enums only, `shared` | `persistence` |
| `application` | `domain`, `persistence` (repository interfaces), `shared` | `api` (no DTOs from controllers) |
| `domain` | JDK, Jakarta Persistence annotations | Spring Web, `api`, `application` |
| `persistence` | `domain`, Spring Data | `api`, `application` |

Rules:
- Controllers are **thin**: parse/validate input, call one application service method, map result to a response DTO.
  No business logic, no repository calls, no `@Transactional`.
- **Entities never leave the application layer.** Controllers receive and return DTOs only.
- Business rules (e.g. the ticket state machine) live in the **domain** (`TicketStatus.canTransitionTo(...)`,
  `Ticket.changeStatus(...)`), not in controllers or services. Services orchestrate.
- Constructor injection only (single constructor, no `@Autowired`). No field injection.
- No circular dependencies between features. Cross-feature calls go through the other feature's `application` service.
- Configuration via `@ConfigurationProperties` records, not scattered `@Value`.

## 3. Validation

- **Backend is the source of truth.** Frontend validation is a UX convenience only.
- Two levels:
  1. **Syntactic** (shape): Jakarta Bean Validation on request DTOs — `@NotBlank`, `@Size`, `@NotNull`, `@Email`, etc.
     Controllers use `@Valid @RequestBody`. Path/query params validated with `@Validated` on the controller.
  2. **Semantic** (business): in the domain/application layer — e.g. invalid status transition, assignee exists,
     ticket not in a terminal state. Throw a specific domain exception.
- Every constraint has a max length that matches the DB column. Constraint limits are defined once
  (constants) and reused by DTO annotations and the migration comment/spec.
- Trim/normalise input deliberately (spec decides); never silently truncate.
- Enum inputs (status, priority) are bound as enums; unknown values produce a `400` with a clear message,
  not a `500`.

## 4. Exception handling

- One global `@RestControllerAdvice` in `shared/error` maps exceptions to **RFC 9457 Problem Details**
  (`org.springframework.http.ProblemDetail`). See `rules/api-standards.md` §5 for the response shape.
- Exception hierarchy (unchecked):
  - `DomainException` (abstract) → `NotFoundException` (404), `InvalidStatusTransitionException` (409),
    `BusinessRuleViolationException` (422), `ConcurrentModificationException` (409, optimistic lock).
- Mapping table must include: `MethodArgumentNotValidException`, `ConstraintViolationException`,
  `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` → 400;
  `ObjectOptimisticLockingFailureException` → 409; anything else → 500 with a generic message.
- **Never leak internals**: no stack traces, SQL, class names, or entity dumps in responses. Log the full
  exception server-side with a correlation id; return the id in the problem body.
- Do not catch-and-ignore. Do not catch `Exception` except in the global handler.
- Don't use exceptions for normal control flow (e.g. use `Optional` for lookups, then throw at the boundary).

## 5. Database practices

- **PostgreSQL** in production and in integration tests (Testcontainers). **H2** only for fast local runs /
  slice tests where SQL dialect doesn't matter (see `rules/testing.md` §4).
- **Schema is owned by Flyway migrations** (`src/main/resources/db/migration/V{n}__description.sql`).
  `spring.jpa.hibernate.ddl-auto=validate` in every profile. Never `update`/`create` outside throwaway spikes.
- Migrations are **immutable once merged**; fix forward with a new version.
- Write migrations in portable SQL where possible. When Postgres-specific features are needed
  (e.g. `ILIKE`, `tsvector`, partial indexes), document it and ensure the H2 profile is not used for tests that touch it.
- Every table: surrogate primary key, `created_at`/`updated_at` (`timestamptz`), `version` column for
  optimistic locking (`@Version`) on mutable aggregates.
- Enums stored as `VARCHAR` with `@Enumerated(EnumType.STRING)` + a `CHECK` constraint. Never `ORDINAL`.
- Constraints in the DB mirror validation: `NOT NULL`, lengths, FKs, `CHECK`s. The DB is the last line of defence.
- Index columns used for filtering/sorting (`status`, `created_at`, FKs).
- JPA:
  - Associations `LAZY` by default. Avoid bidirectional mappings unless needed.
  - Watch for N+1: use fetch joins / `@EntityGraph` for detail views; verify query counts in tests for list endpoints.
  - List endpoints are **always paginated** (`Pageable`), never unbounded `findAll()`.
  - `equals/hashCode` on entities based on id with null-safety, or not overridden — never on all fields.
  - Search queries use bound parameters (Spring Data derived queries, JPQL with params, or `Specification`).
    **Never concatenate user input into SQL/JPQL.**
- Open Session in View **disabled** (`spring.jpa.open-in-view=false`).

## 6. Transaction boundaries

- `@Transactional` lives on **application service public methods** — one use case = one transaction.
- Read-only use cases: `@Transactional(readOnly = true)`.
- No `@Transactional` on controllers, repositories (beyond Spring Data defaults), or private methods
  (proxies ignore them).
- Map entities → DTOs **inside** the transaction (OSIV is off, so lazy loading outside fails fast — that's intended).
- No remote calls / slow I/O inside a transaction.
- Concurrency: optimistic locking via `@Version`; conflicting updates return `409` with a problem type the UI can
  explain ("ticket was modified by someone else — reload").
- Self-invocation doesn't start a transaction — don't call a `@Transactional` method from within the same bean
  and expect new semantics.

## 7. Security and secret handling

See `rules/security.md` (binding for all layers).

## 8. Build

- Build tool: **Gradle** with the **Kotlin DSL** (`build.gradle.kts`, `settings.gradle.kts`). The Gradle wrapper
  (`./gradlew`, `gradle/wrapper/`) is committed; never rely on a locally installed Gradle.
- Java toolchain pinned to 21: `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`.
- Plugins: `org.springframework.boot`, `io.spring.dependency-management`, `jacoco`. Plugin and library versions live in
  the **version catalog** `gradle/libs.versions.toml`; Spring-managed libraries take their version from the Spring Boot BOM.
  No version overrides without a comment explaining why.
- Test tasks:
  - `./gradlew test` — unit and slice tests (`*Test`), no Docker needed.
  - `./gradlew integrationTest` — Testcontainers/`@SpringBootTest` tests (`*IT`), defined as a separate test suite
    (JVM Test Suite plugin, `src/integrationTest/java`) and wired into `check`.
- Build must pass with `./gradlew build` (compile, `test`, `integrationTest`, JaCoCo verification, static checks)
  before any PR.
- Enable the Gradle build cache and configuration cache in `gradle.properties` once the build is stable.
