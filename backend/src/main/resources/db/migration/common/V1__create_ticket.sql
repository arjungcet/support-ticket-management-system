-- spec/data-model.md §3, §9, §10 (REQ-1…4, REQ-7, REQ-8, REQ-11/12). Portable SQL: runs on PostgreSQL and H2.
CREATE TABLE ticket (
    id           BIGINT GENERATED ALWAYS AS IDENTITY,
    title        VARCHAR(200)             NOT NULL,
    description  VARCHAR(5000)            NOT NULL,
    priority     VARCHAR(20)              NOT NULL,
    status       VARCHAR(20)              NOT NULL,
    assignee     VARCHAR(100),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at  TIMESTAMP WITH TIME ZONE,
    closed_at    TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    version      BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_ticket PRIMARY KEY (id),
    CONSTRAINT ck_ticket_title_not_blank CHECK (TRIM(title) <> ''),
    CONSTRAINT ck_ticket_description_not_blank CHECK (TRIM(description) <> ''),
    CONSTRAINT ck_ticket_assignee_not_blank CHECK (assignee IS NULL OR TRIM(assignee) <> ''),
    CONSTRAINT ck_ticket_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_ticket_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_ticket_updated_after_created CHECK (updated_at >= created_at),
    CONSTRAINT ck_ticket_version_non_negative CHECK (version >= 0),
    CONSTRAINT ck_ticket_status_timestamps CHECK (
        (status IN ('OPEN', 'IN_PROGRESS') AND resolved_at IS NULL AND closed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'RESOLVED' AND resolved_at IS NOT NULL AND closed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'CLOSED' AND resolved_at IS NOT NULL AND closed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND resolved_at IS NULL AND closed_at IS NULL AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX ix_ticket_created_at ON ticket (created_at DESC, id DESC);
CREATE INDEX ix_ticket_status_created_at ON ticket (status, created_at DESC, id DESC);
