# Prompt History

A chronological log of every prompt used to build the Support Ticket Management System.
Full per-session transcripts live in `.specstory/history/`.

---

## 2026-09-25 22:20 — Project workflow setup
**Phase:** Repository / AI instructions

> Remember this workflow for the project: Requirement → Repository / AI instructions → Specification → Architecture → Data model → API contract → State machine → Test strategy → Implementation plan → Backend → Frontend → Testing → Review → Fix.
> Also save every prompt under `.specstory/history/` and `docs/prompt-history.md`. If SpecStory is configured, use it to automatically capture Cursor prompts.

---

## 2026-09-25 22:30 — Prompt 0: Initialize the project
**Phase:** Repository / AI instructions
**Full text:** [.specstory/history/2026-09-25_22-30-prompt-0-initialize-project.md](../.specstory/history/2026-09-25_22-30-prompt-0-initialize-project.md)

> Act as a senior software architect and establish a Specification-Driven Development workflow. Do NOT implement the app.
> Create reusable AI engineering guidance: `rules/` (java-springboot, testing, api-standards), `skills/documentation/`,
> `commands/` (review-code, review-spec, generate-tests), `docs/prompt-history.md`, `.specstory/history/`.
> Guidelines must cover Java 21 conventions, Spring Boot architecture, layering, REST conventions, validation, exception
> handling, database practices, transactions, testing, documentation, security/secrets, code review, and AI-generated code
> verification. Stack: Java 21, Spring Boot, PostgreSQL (prod), H2 (tests/local), React/Next.js, JUnit 5, Mockito, Testcontainers.
> Requirements: create/list/view/update tickets, comments, keyword search, status filter, persistence, backend validation,
> meaningful UI errors. State machine: OPEN→IN_PROGRESS→RESOLVED→CLOSED; OPEN→CANCELLED; IN_PROGRESS→CANCELLED; others rejected.
> Report files created, purpose, assumptions, and potential issues.

---

## 2026-09-25 22:40 — Use Gradle
**Phase:** Repository / AI instructions
**Full text:** [.specstory/history/2026-09-25_22-40-use-gradle.md](../.specstory/history/2026-09-25_22-40-use-gradle.md)

> use gradle — switch the build tool assumption from Maven to Gradle across all rules, commands and docs.

---

## 2026-09-25 22:50 — Architecture
**Phase:** Architecture
**Full text:** [.specstory/history/2026-09-25_22-50-prompt-architecture.md](../.specstory/history/2026-09-25_22-50-prompt-architecture.md)

> Review spec/requirements.md and the engineering guidelines, then create `spec/architecture.md` covering backend/frontend
> architecture, packages, layer responsibilities, domain model, DB interaction, DTOs, validation, exceptions, API error
> format, transactions, state transitions, testing, PostgreSQL/H2 usage, configuration, logging, and extensibility.
> Explain where state-machine logic belongs and why not only in the controller. Flag assumptions. No implementation code.

---

## 2026-09-25 23:05 — Data model
**Phase:** Data model
**Full text:** [.specstory/history/2026-09-25_23-05-prompt-data-model.md](../.specstory/history/2026-09-25_23-05-prompt-data-model.md)

> Review requirements + architecture and create `spec/data-model.md`: entities (Ticket, Comment), relationships, PKs, FKs,
> required/nullable fields, enum values, DB constraints, indexes, search considerations, audit timestamps, migration
> strategy. PostgreSQL for production; explain H2/PostgreSQL compatibility. No Java entities or code.

---

## 2026-09-25 23:20 — MySQL or MongoDB?
**Phase:** Data model (question)
**Full text:** [.specstory/history/2026-09-25_23-20-mysql-or-mongodb.md](../.specstory/history/2026-09-25_23-20-mysql-or-mongodb.md)

> can we use mysql or mongodb — asked whether the database choice (PostgreSQL) could be MySQL or MongoDB instead.

---

## 2026-09-25 23:25 — Stay on PostgreSQL
**Phase:** Data model (decision)
**Full text:** [.specstory/history/2026-09-25_23-25-stay-on-postgresql.md](../.specstory/history/2026-09-25_23-25-stay-on-postgresql.md)

> ok stay on postgres sql — MySQL and MongoDB rejected; PostgreSQL confirmed. Recorded as ADR-0001.

---

## 2026-09-25 23:35 — API contract
**Phase:** API contract
**Full text:** [.specstory/history/2026-09-25_23-35-prompt-api-contract.md](../.specstory/history/2026-09-25_23-35-prompt-api-contract.md)

> Review all spec documents and create `spec/api-contract.md`: REST contract for create, list, get, update ticket,
> update assignee, add comment, search, filter by status, change status — method, URL, bodies, params, responses,
> status codes, validation/business/not-found/invalid-transition behaviour — with one consistent error structure,
> suitable for frontend and automated tests. No code.

---

## 2026-09-25 23:50 — State machine
**Phase:** State machine
**Full text:** [.specstory/history/2026-09-25_23-50-prompt-state-machine.md](../.specstory/history/2026-09-25_23-50-prompt-state-machine.md)

> Review requirements + API contract and create `spec/state-machine.md`: complete backend ticket state machine with an
> explicit Current | Requested | Allowed/Rejected matrix; API request, success/error responses, business exception,
> concurrency, persistence, transaction boundary, testing. Backend must enforce it for every client (UI, Postman, curl,
> other services). No code.

---

## 2026-09-26 00:05 — Test strategy
**Phase:** Test strategy
**Full text:** [.specstory/history/2026-09-26_00-05-prompt-test-strategy.md](../.specstory/history/2026-09-26_00-05-prompt-test-strategy.md)

> Review all specs and create `spec/test-strategy.md`: unit, service, controller, repository, integration, state-machine,
> validation, API error, search, status-filter, persistence/restart, frontend and E2E tests. Explicit allowed tests for the
> 5 transitions and explicit rejection tests incl. CLOSED→OPEN, RESOLVED→OPEN, CANCELLED→OPEN. Define the test pyramid and
> which tests catch which defect classes. No production code.

---

## 2026-09-26 00:20 — Skeptical specification review
**Phase:** Review (specification)
**Full text:** [.specstory/history/2026-09-26_00-20-prompt-spec-review.md](../.specstory/history/2026-09-26_00-20-prompt-spec-review.md)

> Act as a skeptical senior engineer and review every file under `spec/` for missing requirements, contradictions,
> ambiguity, wrong assumptions, API/DB/state-machine/validation/testing/security gaps, FE/BE mismatches, PostgreSQL/H2
> incompatibilities and untestable requirements. For each: issue, why it matters, correction, affected file.
> Do not modify the specs.

---

## 2026-09-26 00:40 — Implementation plan
**Phase:** Implementation plan
**Full text:** [.specstory/history/2026-09-26_00-40-prompt-implementation-plan.md](../.specstory/history/2026-09-26_00-40-prompt-implementation-plan.md)

> Specs approved for implementation planning. Create a detailed plan of small, independently reviewable tasks across
> 20 phases (setup → DB → domain → repository → service → state machine → REST → validation/errors → comments →
> search/filter → backend tests → frontend setup/tickets/comments/search/errors → E2E → security/config → docs → final
> review). Each task: objective, files, dependencies, acceptance criteria, tests. No implementation.

---

## 2026-09-26 01:00 — Implement first backend task
**Phase:** Backend (STEP-07)
**Full text:** [.specstory/history/2026-09-26_01-00-prompt-first-backend-task.md](../.specstory/history/2026-09-26_01-00-prompt-first-backend-task.md)

> Implement only the first backend task from the implementation plan: read specs and rules, identify acceptance criteria,
> implement only that scope with tests, run them; stop and report if the spec is ambiguous. Report files, requirements,
> tests, results, assumptions, deviations.

---

## 2026-09-26 01:30 — Generate missing automated tests
**Phase:** Testing
**Full text:** [.specstory/history/2026-09-26_01-30-prompt-generate-tests.md](../.specstory/history/2026-09-26_01-30-prompt-generate-tests.md)

> Review the implementation against test-strategy, state-machine and api-contract specs; generate missing behaviour-focused
> tests (creation, retrieval, listing, update, assignee, comments, search, status filter, validation, not-found, invalid and
> valid transitions — every allowed and representative invalid transition). Don't change production code to make tests
> pass. Run them and report test failures separately from implementation defects.

---

## 2026-09-26 02:10 — Implement next frontend task
**Phase:** Frontend
**Full text:** [.specstory/history/2026-09-26_02-10-prompt-frontend.md](../.specstory/history/2026-09-26_02-10-prompt-frontend.md)

> Implement the next frontend task after reading the API contract, frontend requirements, guidelines and the backend API.
> Must support create/list/view/edit, assignee, comments, search, status filter, status transition, meaningful validation
> and business errors. No invented fields or endpoints; contract is the source of truth. Run tests, verify API integration,
> report files changed and contract mismatches.

---

## 2026-09-26 02:50 — Integration / E2E testing
**Phase:** Testing (integration/E2E)
**Full text:** [.specstory/history/2026-09-26_02-50-prompt-e2e.md](../.specstory/history/2026-09-26_02-50-prompt-e2e.md)

> As an integration-test engineer, review the app against all specs, identify 13 critical E2E journeys (create, list, open,
> update, assignee, comment, search, filter, valid/invalid transition, validation failure, backend error in UI,
> persistence after restart), create/update integration/E2E tests, run them, and classify every failure (test /
> implementation / specification / environment defect) without hiding any. Don't modify production code first.
