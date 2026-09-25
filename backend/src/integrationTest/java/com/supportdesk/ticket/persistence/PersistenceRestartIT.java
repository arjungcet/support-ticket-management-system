package com.supportdesk.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.SupportDeskApplication;
import com.supportdesk.support.PostgresTestcontainer;
import com.supportdesk.ticket.application.TicketCommands.AddComment;
import com.supportdesk.ticket.application.TicketCommands.ChangeStatus;
import com.supportdesk.ticket.application.TicketCommands.CreateTicket;
import com.supportdesk.ticket.application.TicketCommentService;
import com.supportdesk.ticket.application.TicketService;
import com.supportdesk.ticket.application.TicketViews.TicketView;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketStatus;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * REQ-8 / AC-11 — data survives an application restart (spec/test-strategy.md §11, TS-PERS-01…06): two application
 * contexts, one after the other, against the same PostgreSQL database.
 */
class PersistenceRestartIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestcontainer.IMAGE);

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(SupportDeskApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword())
                .run();
    }

    @Test
    @DisplayName("tickets, status, comments and versions survive a restart; migrations aren't re-applied; ids continue")
    void dataSurvivesRestart() {
        TicketView before;
        try (ConfigurableApplicationContext first = startApplication()) {
            TicketService tickets = first.getBean(TicketService.class);
            TicketView created = tickets.create(new CreateTicket("Durable", "Must survive", TicketPriority.HIGH, "sam"));
            before = tickets.changeStatus(new ChangeStatus(created.id(), created.version(), TicketStatus.IN_PROGRESS));
            first.getBean(TicketCommentService.class).add(new AddComment(created.id(), "maria", "Before the restart"));
        }

        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")); // TS-PERS-05
        try (ConfigurableApplicationContext second = startApplication()) {
            TicketService tickets = second.getBean(TicketService.class);

            // TS-PERS-01: identical after restart
            assertThat(tickets.get(before.id())).isEqualTo(before);
            assertThat(second.getBean(TicketCommentService.class).list(before.id(), 0, 50).content())
                    .singleElement()
                    .satisfies(comment -> assertThat(comment.body()).isEqualTo("Before the restart"));
            // TS-PERS-02: migrations ran once
            Integer migrations = second.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
            assertThat(migrations).as("V1, V2 (common) and V3 (PostgreSQL search indexes), each once").isEqualTo(3);
            // TS-PERS-03: identity continues
            TicketView next = tickets.create(new CreateTicket("After", "restart", TicketPriority.LOW, null));
            assertThat(next.id()).isGreaterThan(before.id());
            // TS-PERS-04: lifecycle continues with the pre-restart version
            TicketView resolved = tickets.changeStatus(
                    new ChangeStatus(before.id(), before.version(), TicketStatus.RESOLVED));
            assertThat(resolved.status()).isEqualTo(TicketStatus.RESOLVED);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
