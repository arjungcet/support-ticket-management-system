# 2026-09-25 23:05 — Data model

**Phase:** Data model

## Prompt

Review:

spec/requirements.md
spec/architecture.md

Do NOT implement Java entities yet.

Create:

spec/data-model.md

Design the persistence model for the Support Ticket Management System.

At minimum consider:

Ticket:
- id
- title
- description
- priority
- status
- assignee
- createdAt
- updatedAt

Comment:
- id
- ticketId
- comment text
- createdAt
- author if appropriate

Define:

1. Entities
2. Relationships
3. Primary keys
4. Foreign keys
5. Required fields
6. Nullable fields
7. Enum values
8. Database constraints
9. Indexes
10. Search considerations
11. Audit timestamps
12. Migration strategy

Use PostgreSQL as the production database.

Explain any H2/PostgreSQL compatibility considerations.

Do not implement code.
