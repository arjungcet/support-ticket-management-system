package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static com.supportdesk.support.ProblemAssert.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** REQ-5 — spec/api-contract.md §6.6, §6.3a, §4.3. Field-level validation lives in {@link BackendValidationApiIT}. */
@SpecAcceptanceTest
@DisplayName("REQ-5 Comments")
class CommentApiIT extends ApiTestBase {

    @Test
    @DisplayName("adds a comment: 201, Location and the comment representation")
    void post_addsComment() {
        long ticketId = tickets.create("Title", "Description").number("id");

        ApiResponse response = api.post(TicketApi.comments(ticketId),
                json("author", "maria.lopez", "body", "Reset the account lock; asked user to retry."));

        assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(201);
        long commentId = response.number("id");
        assertThat(response.header("Location")).isEqualTo(TicketApi.comments(ticketId) + "/" + commentId);
        assertThat(response.body()).containsOnlyKeys("id", "ticketId", "author", "body", "createdAt");
        assertThat(response.number("ticketId")).isEqualTo(ticketId);
        assertThat(response.string("author")).isEqualTo("maria.lopez");
        assertThat(response.string("body")).isEqualTo("Reset the account lock; asked user to retry.");
        assertThat(response.instant("createdAt")).isNotNull();
    }

    @Test
    @DisplayName("author and body are trimmed")
    void post_trimsText() {
        long ticketId = tickets.create("Title", "Description").number("id");

        ApiResponse response = tickets.addComment(ticketId, "  maria.lopez ", "\n  Looking into it  ");

        assertThat(response.string("author")).isEqualTo("maria.lopez");
        assertThat(response.string("body")).isEqualTo("Looking into it");
    }

    @Test
    @DisplayName("comments are listed oldest first and only for their own ticket")
    void get_listsCommentsOldestFirst() {
        long ticketId = tickets.create("Title", "Description").number("id");
        long otherTicketId = tickets.create("Other", "Other").number("id");
        long first = tickets.addComment(ticketId, "a", "first").number("id");
        tickets.addComment(otherTicketId, "b", "belongs elsewhere");
        long second = tickets.addComment(ticketId, "c", "second").number("id");

        ApiResponse response = api.get(TicketApi.comments(ticketId));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.contentIds()).containsExactly(first, second);
        assertThat(response.page()).containsEntry("number", 0).containsEntry("size", 50)
                .containsEntry("totalElements", 2).containsEntry("totalPages", 1);
        List<Map<String, Object>> content = response.content();
        assertThat(content.get(0)).containsEntry("body", "first").containsEntry("author", "a");
    }

    @Test
    @DisplayName("comments are paginated")
    void get_paginatesComments() {
        long ticketId = tickets.create("Title", "Description").number("id");
        long c1 = tickets.addComment(ticketId, "a", "1").number("id");
        long c2 = tickets.addComment(ticketId, "a", "2").number("id");
        long c3 = tickets.addComment(ticketId, "a", "3").number("id");

        ApiResponse page0 = api.get(TicketApi.comments(ticketId) + "?size=2&page=0");
        ApiResponse page1 = api.get(TicketApi.comments(ticketId) + "?size=2&page=1");

        assertThat(page0.contentIds()).containsExactly(c1, c2);
        assertThat(page1.contentIds()).containsExactly(c3);
        assertThat(page0.page()).containsEntry("totalElements", 3).containsEntry("totalPages", 2);
    }

    @Test
    @DisplayName("a ticket without comments returns an empty page")
    void get_noComments_isEmptyPage() {
        long ticketId = tickets.create("Title", "Description").number("id");

        ApiResponse response = api.get(TicketApi.comments(ticketId));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).containsEntry("totalElements", 0).containsEntry("totalPages", 0);
    }

    @Test
    @DisplayName("adding a comment does not change the ticket's version or updatedAt (A-34, DM-6)")
    void post_doesNotTouchTicket() {
        ApiResponse ticket = tickets.create("Title", "Description");
        long id = ticket.number("id");

        tickets.addComment(id, "maria.lopez", "note");

        ApiResponse fetched = tickets.get(id);
        assertThat(fetched.number("version")).isEqualTo(ticket.number("version"));
        assertThat(fetched.instant("updatedAt")).isEqualTo(ticket.instant("updatedAt"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    @DisplayName("comments are allowed on non-terminal tickets (A-18)")
    void post_nonTerminalTicket_isAllowed(String status) {
        long ticketId = tickets.createInStatus(status).number("id");

        ApiResponse response = api.post(TicketApi.comments(ticketId), json("author", "a", "body", "b"));

        assertThat(response.status()).isEqualTo(201);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CLOSED", "CANCELLED"})
    @DisplayName("commenting on a terminal ticket returns 422 TICKET_NOT_COMMENTABLE and adds nothing (A-18)")
    void post_terminalTicket_isRejected(String status) {
        long ticketId = tickets.createInStatus(status).number("id");

        ApiResponse response = api.post(TicketApi.comments(ticketId), json("author", "a", "body", "too late"));

        assertProblem(response, 422, "TICKET_NOT_COMMENTABLE");
        assertThat(response.number("ticketId")).isEqualTo(ticketId);
        assertThat(response.string("currentStatus")).isEqualTo(status);
        assertThat(api.get(TicketApi.comments(ticketId)).content()).isEmpty();
    }

    @Test
    @DisplayName("adding a comment to an unknown ticket returns 404 TICKET_NOT_FOUND")
    void post_unknownTicket_returns404() {
        ApiResponse response = api.post(TicketApi.comments(987_654_321_012L), json("author", "a", "body", "b"));

        assertProblem(response, 404, "TICKET_NOT_FOUND");
    }

    @Test
    @DisplayName("listing comments of an unknown ticket returns 404 TICKET_NOT_FOUND")
    void get_unknownTicket_returns404() {
        ApiResponse response = api.get(TicketApi.comments(987_654_321_012L));

        assertProblem(response, 404, "TICKET_NOT_FOUND");
    }
}
