-- PostgreSQL only (spec/data-model.md §13.3, upgrade step 1; ADR-0001). Measured need: with 100 000 tickets a search
-- with no match scanned the whole table (p95 545 ms > the 500 ms target of D-4). Trigram GIN indexes let the existing
-- lower(column) LIKE '%…%' queries use an index with no query change. H2 skips this folder; queries stay correct there.
-- pg_trgm is a trusted extension (PostgreSQL 13+): the database owner can create it without superuser rights.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_ticket_title_trgm ON ticket USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_ticket_description_trgm ON ticket USING gin (lower(description) gin_trgm_ops);
