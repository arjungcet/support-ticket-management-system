-- spec/data-model.md §4, §6, §9, §10 (REQ-5, REQ-8). Comments are append-only: no updated_at, no version (DM-1).
CREATE TABLE ticket_comment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY,
    ticket_id  BIGINT                   NOT NULL,
    author     VARCHAR(100)             NOT NULL,
    body       VARCHAR(5000)            NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_ticket_comment PRIMARY KEY (id),
    CONSTRAINT fk_ticket_comment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket (id) ON DELETE RESTRICT,
    CONSTRAINT ck_ticket_comment_author_not_blank CHECK (TRIM(author) <> ''),
    CONSTRAINT ck_ticket_comment_body_not_blank CHECK (TRIM(body) <> '')
);

CREATE INDEX ix_ticket_comment_ticket_created ON ticket_comment (ticket_id, created_at, id);
