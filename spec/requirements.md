# Requirements — Support Ticket Management System

| Status | Last updated | Related |
|--------|--------------|---------|
| Approved (as given by product owner) | 2026-09-25 | `AGENTS.md`, `docs/prompt-history.md` (Prompt 0) |

Recorded verbatim from Prompt 0. Detailed behaviour is defined in the Specification phase (`spec/functional-spec.md`).

## Functional requirements

| ID | Requirement |
|----|-------------|
| REQ-1 | Create a ticket. |
| REQ-2 | List tickets. |
| REQ-3 | View ticket details. |
| REQ-4 | Update title, description, priority and assignee. |
| REQ-5 | Add comments. |
| REQ-6 | Search tickets by keyword. |
| REQ-7 | Filter tickets by status. |
| REQ-8 | Persist data in a database. |
| REQ-9 | Validate input at the backend. |
| REQ-10 | Display meaningful errors in the UI. |

## Ticket state machine

| ID | Requirement |
|----|-------------|
| REQ-11 | Allowed transitions: `OPEN → IN_PROGRESS`, `IN_PROGRESS → RESOLVED`, `RESOLVED → CLOSED`, `OPEN → CANCELLED`, `IN_PROGRESS → CANCELLED`. |
| REQ-12 | Invalid transitions MUST be rejected by the backend (e.g. `CLOSED → OPEN`, `RESOLVED → OPEN`, `CANCELLED → OPEN`). |

## Technology constraints

| ID | Constraint |
|----|------------|
| TC-1 | Java 21, Spring Boot, REST API |
| TC-2 | PostgreSQL in production; H2 for tests/local lightweight execution where appropriate |
| TC-3 | React / Next.js frontend |
| TC-4 | Maven or Gradle (**Gradle, Kotlin DSL** chosen — see `rules/java-springboot.md` §8) |
| TC-5 | JUnit 5, Spring Boot Test, Mockito and Testcontainers where appropriate |
