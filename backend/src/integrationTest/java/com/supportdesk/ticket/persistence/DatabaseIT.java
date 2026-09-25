package com.supportdesk.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supportdesk.support.PostgresTestcontainer;
import com.supportdesk.ticket.application.TicketCommands.CreateTicket;
import com.supportdesk.ticket.application.TicketService;
import com.supportdesk.ticket.application.TicketViews.TicketView;
import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import com.supportdesk.ticket.domain.TicketSearchCriteria.SortField;
import com.supportdesk.ticket.domain.TicketStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database behaviour on real PostgreSQL (spec/test-strategy.md §4.4: TS-REPO-01…07). The schema comes from the Flyway
 * migrations and Hibernate validates it on startup (TS-REPO-01).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(PostgresTestcontainer.class)
class DatabaseIT {

    private static final String INSERT_TICKET = """
            INSERT INTO ticket (title, description, priority, status, assignee, created_at, updated_at,
                                resolved_at, closed_at, cancelled_at, version)
            VALUES (?, ?, ?, ?, ?, TIMESTAMP WITH TIME ZONE '2026-09-26 08:00:00+00', CAST(? AS TIMESTAMP WITH TIME ZONE),
                    CAST(? AS TIMESTAMP WITH TIME ZONE), CAST(? AS TIMESTAMP WITH TIME ZONE),
                    CAST(? AS TIMESTAMP WITH TIME ZONE), ?)
            """;
    private static final String T = "2026-09-26 09:00:00+00";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private Clock clock;

    private void insertTicket(String title, String description, String priority, String status, String assignee,
            String updatedAt, String resolvedAt, String closedAt, String cancelledAt, long version) {
        jdbc.update(INSERT_TICKET, title, description, priority, status, assignee, updatedAt, resolvedAt, closedAt,
                cancelledAt, version);
    }

    private long validTicketId() {
        insertTicket("t", "d", "LOW", "OPEN", null, T, null, null, null, 0);
        return jdbc.queryForObject("SELECT max(id) FROM ticket", Long.class);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "ck_ticket_title_not_blank           | '   '  | d | LOW      | OPEN     | null | 0",
            "ck_ticket_description_not_blank     | t      |   | LOW      | OPEN     | null | 0",
            "ck_ticket_assignee_not_blank        | t      | d | LOW      | OPEN     | ' '  | 0",
            "ck_ticket_priority                  | t      | d | CRITICAL | OPEN     | null | 0",
            "ck_ticket_status                    | t      | d | LOW      | REOPENED | null | 0",
            "ck_ticket_version_non_negative      | t      | d | LOW      | OPEN     | null | -1"})
    @DisplayName("TS-REPO-02 each column constraint rejects a violating row")
    void columnConstraints(String constraint, String title, String description, String priority, String status,
            String assignee, long version) {
        String blankAware = description == null ? " " : description;

        assertThatThrownBy(() -> insertTicket(title, blankAware, priority, status, assignee, T, null, null, null,
                version))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    @Test
    @DisplayName("search: trigram indexes exist and serve the lower(col) LIKE '%…%' search shape (data-model §13.3)")
    void searchUsesTrigramIndexes() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'ticket' AND indexname LIKE '%trgm'", String.class);
        assertThat(indexes).containsExactlyInAnyOrder("ix_ticket_title_trgm", "ix_ticket_description_trgm");

        // With sequential scans disabled the planner must be able to answer the search from the indexes alone.
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                var result = statement.executeQuery("""
                        EXPLAIN SELECT id FROM ticket
                        WHERE lower(title) LIKE '%printer%' ESCAPE '\\' OR lower(description) LIKE '%printer%' ESCAPE '\\'""");
                StringBuilder lines = new StringBuilder();
                while (result.next()) {
                    lines.append(result.getString(1)).append('\n');
                }
                statement.execute("RESET enable_seqscan");
                return lines.toString();
            }
        });
        assertThat(plan).contains("ix_ticket_title_trgm").contains("ix_ticket_description_trgm");
    }

    @Test
    @DisplayName("TS-REPO-02 updated_at before created_at is rejected")
    void updatedBeforeCreated() {
        assertThatThrownBy(() -> insertTicket("t", "d", "LOW", "OPEN", null, "2026-09-26 07:00:00+00", null, null,
                null, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ticket_updated_after_created");
    }

    @ParameterizedTest(name = "{0} resolved={1} closed={2} cancelled={3} → allowed={4}")
    @CsvSource(nullValues = "null", value = {
            "OPEN,null,null,null,true", "IN_PROGRESS,null,null,null,true", "RESOLVED,T,null,null,true",
            "CLOSED,T,T,null,true", "CANCELLED,null,null,T,true",
            "OPEN,T,null,null,false", "RESOLVED,null,null,null,false", "CLOSED,T,null,null,false",
            "CLOSED,null,T,null,false", "CANCELLED,null,null,null,false", "CANCELLED,T,null,T,false"})
    @DisplayName("TS-REPO-02 / TS-SM-E5 status and lifecycle timestamps must agree (ck_ticket_status_timestamps)")
    void statusTimestamps(String status, String resolved, String closed, String cancelled, boolean allowed) {
        Runnable insert = () -> insertTicket("t", "d", "LOW", status, null, T,
                "T".equals(resolved) ? T : null, "T".equals(closed) ? T : null, "T".equals(cancelled) ? T : null, 0);

        if (allowed) {
            insert.run();
        } else {
            assertThatThrownBy(insert::run)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_ticket_status_timestamps");
        }
    }

    @Test
    @DisplayName("TS-REPO-04 ids are generated; an explicit id is rejected (GENERATED ALWAYS)")
    void identity() {
        long first = validTicketId();
        long second = validTicketId();

        assertThat(second).isGreaterThan(first);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ticket (id, title, description, priority, status, created_at, updated_at, version)
                VALUES (999999, 't', 'd', 'LOW', 'OPEN', now(), now(), 0)
                """))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("non-DEFAULT value");
    }

    @Test
    @DisplayName("TS-REPO-02/03 comment constraints, FK to ticket, and ON DELETE RESTRICT")
    void commentConstraints() {
        long ticketId = validTicketId();
        String insertComment = "INSERT INTO ticket_comment (ticket_id, author, body, created_at) VALUES (?, ?, ?, now())";

        assertThatThrownBy(() -> jdbc.update(insertComment, ticketId, " ", "b"))
                .hasMessageContaining("ck_ticket_comment_author_not_blank");
        assertThatThrownBy(() -> jdbc.update(insertComment, ticketId, "a", " "))
                .hasMessageContaining("ck_ticket_comment_body_not_blank");
        assertThatThrownBy(() -> jdbc.update(insertComment, 987_654_321L, "a", "b"))
                .hasMessageContaining("fk_ticket_comment_ticket");

        jdbc.update(insertComment, ticketId, "a", "b");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ticket WHERE id = ?", ticketId))
                .hasMessageContaining("fk_ticket_comment_ticket");
    }

    @Test
    @DisplayName("TS-REPO-05 timestamps round-trip exactly (µs, UTC) even when the JVM default time zone isn't UTC")
    void timestampsRoundTrip() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            TicketView created = ticketService.create(new CreateTicket("Clock", "Round trip", TicketPriority.LOW, null));

            TicketView reloaded = ticketService.get(created.id());

            assertThat(reloaded.createdAt()).isEqualTo(created.createdAt());
            assertThat(reloaded.updatedAt()).isEqualTo(created.updatedAt());
            assertThat(created.createdAt().getNano() % 1_000).isZero();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("TS-REPO-06 optimistic lock: the second of two concurrent updates of one version fails at commit")
    void optimisticLock() {
        long id = ticketService.create(new CreateTicket("Race", "Two writers", TicketPriority.LOW, null)).id();
        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Ticket seenByFirst = first.find(Ticket.class, id);
            Ticket seenBySecond = second.find(Ticket.class, id);

            seenByFirst.changeStatus(TicketStatus.IN_PROGRESS, clock);
            first.getTransaction().commit();
            seenBySecond.changeStatus(TicketStatus.CANCELLED, clock);

            assertThatThrownBy(() -> second.getTransaction().commit())
                    .isInstanceOf(RollbackException.class)
                    .hasCauseInstanceOf(OptimisticLockException.class);
            assertThat(ticketService.get(id).status()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(ticketService.get(id).version()).isEqualTo(1);
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    @DisplayName("TS-REPO-07 a list page is one content query plus one count query and loads no entities")
    void listIsAProjection() {
        ticketService.create(new CreateTicket("Projection", "not loaded", TicketPriority.LOW, null));
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        ticketService.search(new TicketSearchCriteria("", Set.of(), SortField.PRIORITY, false), 0, 20);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isZero();
    }
}
