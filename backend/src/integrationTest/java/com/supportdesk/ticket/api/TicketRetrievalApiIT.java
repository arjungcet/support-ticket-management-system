package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static com.supportdesk.support.ProblemAssert.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** REQ-3 — spec/api-contract.md §6.3. */
@SpecAcceptanceTest
@DisplayName("REQ-3 View ticket details")
class TicketRetrievalApiIT extends ApiTestBase {

    @Test
    @DisplayName("returns 200 with the full ticket representation")
    void get_existingTicket_returnsIt() {
        ApiResponse created = tickets.create(json(
                "title", "Email bounce", "description", "Outbound mail rejected", "priority", "LOW",
                "assignee", "sam.ops"));

        ApiResponse response = api.get(TicketApi.ticket(created.number("id")));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.number("id")).isEqualTo(created.number("id"));
        assertThat(response.string("title")).isEqualTo("Email bounce");
        assertThat(response.string("description")).isEqualTo("Outbound mail rejected");
        assertThat(response.string("priority")).isEqualTo("LOW");
        assertThat(response.string("assignee")).isEqualTo("sam.ops");
        assertThat(response.string("status")).isEqualTo("OPEN");
        assertThat(response.number("version")).isZero();
        assertThat(response.list("allowedTransitions")).containsExactly("IN_PROGRESS", "CANCELLED");
    }

    @Test
    @DisplayName("nullable properties are present with value null, never omitted")
    void get_nullablePropertiesArePresent() {
        long id = tickets.create("Email bounce", "Outbound mail rejected").number("id");

        ApiResponse response = api.get(TicketApi.ticket(id));

        assertThat(response.body())
                .containsEntry("assignee", null)
                .containsEntry("resolvedAt", null)
                .containsEntry("closedAt", null)
                .containsEntry("cancelledAt", null);
    }

    @Test
    @DisplayName("does not embed comments (they are fetched from /comments)")
    void get_doesNotEmbedComments() {
        long id = tickets.create("Email bounce", "Outbound mail rejected").number("id");
        tickets.addComment(id, "sam.ops", "Looking into it");

        ApiResponse response = api.get(TicketApi.ticket(id));

        assertThat(response.body()).doesNotContainKey("comments");
    }

    @Test
    @DisplayName("unknown id returns 404 TICKET_NOT_FOUND with the ticketId")
    void get_unknownTicket_returns404() {
        long unknownId = 987_654_321_012L;

        ApiResponse response = api.get(TicketApi.ticket(unknownId));

        assertProblem(response, 404, "TICKET_NOT_FOUND");
        assertThat(response.number("ticketId")).isEqualTo(unknownId);
        assertThat(response.string("instance")).isEqualTo(TicketApi.ticket(unknownId));
    }
}
