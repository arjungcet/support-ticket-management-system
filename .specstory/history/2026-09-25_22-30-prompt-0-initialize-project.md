# 2026-09-25 22:30 — Prompt 0: Initialize the project

**Phase:** Repository / AI instructions

## Prompt

You are acting as a senior software architect helping me establish a Specification-Driven Development (SDD) workflow.

Do NOT implement the Support Ticket Management System yet.

First inspect the repository and establish the project structure and AI engineering guidelines.

The application we will eventually build is: Support Ticket Management System

Technology:
- Java 21
- Spring Boot
- PostgreSQL for production
- H2 for tests/local lightweight execution where appropriate
- REST API
- React / Next.js frontend
- Maven or Gradle
- JUnit 5
- Spring Boot Test
- Mockito where appropriate
- Testcontainers where appropriate

The application requirements are:
1. Create a ticket.
2. List tickets.
3. View ticket details.
4. Update title, description, priority and assignee.
5. Add comments.
6. Search tickets by keyword.
7. Filter tickets by status.
8. Persist data in a database.
9. Validate input at the backend.
10. Display meaningful errors in the UI.

Ticket state machine:

OPEN → IN_PROGRESS → RESOLVED → CLOSED
OPEN → CANCELLED
IN_PROGRESS → CANCELLED

Invalid transitions must be rejected by the backend.
Examples: CLOSED → OPEN, RESOLVED → OPEN, CANCELLED → OPEN

Before writing application code, create reusable AI engineering guidance.

Create:

rules/
  java-springboot.md
  testing.md
  api-standards.md

skills/
  documentation/

commands/
  review-code.md
  review-spec.md
  generate-tests.md

Also create:

docs/
  prompt-history.md

.specstory/
  history/

The exact structure may be adapted to Cursor conventions, but reusable project-level AI instructions must exist.

The guidelines must cover:
- Java 21 coding conventions
- Spring Boot architecture
- layering and dependency direction
- REST API conventions
- validation
- exception handling
- database practices
- transaction boundaries
- testing standards
- documentation standards
- security and secret handling
- code review expectations
- AI-generated code verification

Do not create business/application implementation yet.

At the end, show me:
1. Files created
2. Purpose of each file
3. Any assumptions made
4. Any potential issues with the proposed structure

Do not proceed to implementation.
