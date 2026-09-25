# ADR-0001: Use PostgreSQL as the production database

| Status | Date | Supersedes | Superseded by |
|--------|------|------------|---------------|
| Accepted | 2026-09-25 | — | — |

## Context

REQ-8 requires persistence, and TC-2 names PostgreSQL for production with H2 for lightweight local runs. The data is
relational: tickets own comments, fields have strict limits, and the ticket lifecycle is backed by database
constraints (`spec/data-model.md` §10). Search is a case-insensitive substring match with a planned index upgrade
path. The product owner asked whether MySQL or MongoDB could be used instead.

## Options considered

1. **PostgreSQL**
   - Pros: full `CHECK`/FK enforcement; `timestamptz`; standard identity columns; `pg_trgm` and full-text search
     available as upgrades without changing the query shape; mature Flyway and Testcontainers support; H2 has a
     PostgreSQL compatibility mode.
   - Cons: none significant for this workload.
2. **MySQL 8.0.16+**
   - Pros: workable with the same JPA/Flyway architecture.
   - Cons: accent-insensitive default collation changes search semantics; no `timestamptz` (`DATETIME(6)` UTC
     convention needed); `FULLTEXT` is word-based rather than substring; row-size limit pressure with two
     `varchar(5000)` columns; `CHECK` only enforced from 8.0.16.
3. **MongoDB**
   - Pros: flexible schema, horizontal scale.
   - Cons: no foreign keys or `CHECK` constraints; comment modelling is awkward (16 MB document limit if embedded,
     join without integrity if separate); substring search can't use an index without Atlas Search; multi-document
     transactions need a replica set; replaces JPA, Flyway and H2, which means rewriting most design documents.

## Decision

Use **PostgreSQL** (16+, ⚠ A-2) in production and in all database-behaviour tests (Testcontainers). H2 in PostgreSQL
mode stays an optional no-Docker fallback only (`spec/architecture.md` §13).

## Consequences

- `spec/data-model.md` and `spec/architecture.md` §9, §12–13 stand as written.
- PostgreSQL-specific features (`pg_trgm`, full-text search, partial indexes) are allowed, but only in
  `db/migration/postgresql` and only if they degrade gracefully on H2.
- Revisit this decision only if the organisation mandates a different database platform.
