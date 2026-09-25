# 2026-09-25 22:50 — Architecture

**Phase:** Architecture

## Prompt

Review spec/requirements.md and the project-level engineering guidelines.

Do NOT implement application code.

Create:

spec/architecture.md

Define the proposed architecture for the Support Ticket Management System.

The architecture should cover:

- Backend architecture
- Frontend architecture
- Package/module structure
- Controller/service/repository responsibilities
- Domain model responsibilities
- Database interaction
- DTO strategy
- Validation strategy
- Exception handling
- API error format
- Transaction boundaries
- State transition handling
- Testing architecture
- PostgreSQL usage
- H2 usage
- Configuration management
- Logging
- Future extensibility

Prefer a clean, maintainable Spring Boot architecture.

Explicitly explain where the state-machine business logic belongs and why it should not be implemented only in the controller.

Do not write implementation code.

Flag any architectural decision that requires an assumption.
