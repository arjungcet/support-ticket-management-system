package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static com.supportdesk.support.ProblemAssert.assertProblem;
import static com.supportdesk.support.ProblemAssert.assertValidationErrors;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.ProblemAssert.FieldError;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** REQ-4 (assignee) — spec/api-contract.md §6.5. */
@SpecAcceptanceTest
@DisplayName("REQ-4 Update assignee")
class AssigneeApiIT extends ApiTestBase {

    @Test
    @DisplayName("assigns a ticket: 200, assignee set, version bumped, status unchanged (A-19)")
    void put_assignsTicket() {
        long id = tickets.create("Title", "Description").number("id");

        ApiResponse response = api.put(TicketApi.assignee(id), json("version", 0, "assignee", "maria.lopez"));

        assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(200);
        assertThat(response.string("assignee")).isEqualTo("maria.lopez");
        assertThat(response.number("version")).isEqualTo(1);
        assertThat(response.string("status")).isEqualTo("OPEN");
        assertThat(tickets.get(id).string("assignee")).isEqualTo("maria.lopez");
    }

    @Test
    @DisplayName("reassigns to someone else")
    void put_reassigns() {
        long id = tickets.create("Title", "Description").number("id");
        api.put(TicketApi.assignee(id), json("version", 0, "assignee", "maria.lopez"));

        ApiResponse response = api.put(TicketApi.assignee(id), json("version", 1, "assignee", "sam.ops"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.string("assignee")).isEqualTo("sam.ops");
        assertThat(response.number("version")).isEqualTo(2);
    }

    @Test
    @DisplayName("assignee null unassigns the ticket")
    void put_null_unassigns() {
        long id = tickets.create("Title", "Description").number("id");
        api.put(TicketApi.assignee(id), json("version", 0, "assignee", "maria.lopez"));

        ApiResponse response = api.put(TicketApi.assignee(id), json("version", 1, "assignee", null));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).containsEntry("assignee", null);
        assertThat(tickets.get(id).get("assignee")).isNull();
    }

    @ParameterizedTest(name = "assignee \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank assignee unassigns the ticket (DM-4)")
    void put_blank_unassigns(String blank) {
        long id = tickets.create(json("title", "t", "description", "d", "assignee", "maria.lopez")).number("id");

        ApiResponse response = api.put(TicketApi.assignee(id), json("version", 0, "assignee", blank));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.get("assignee")).isNull();
    }

    @Test
    @DisplayName("assigning the current assignee again is a no-op: 200, version unchanged")
    void put_sameAssignee_isNoOp() {
        long id = tickets.create(json("title", "t", "description", "d", "assignee", "maria.lopez")).number("id");

        ApiResponse response = api.put(TicketApi.assignee(id), json("version", 0, "assignee", "maria.lopez"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.number("version")).isZero();
    }

    @Test
    @DisplayName("assigning is allowed while RESOLVED (A-17)")
    void put_resolvedTicket_isAllowed() {
        ApiResponse resolved = tickets.createInStatus("RESOLVED");

        ApiResponse response = api.put(TicketApi.assignee(resolved.number("id")),
                json("version", resolved.number("version"), "assignee", "maria.lopez"));

        assertThat(response.status()).isEqualTo(200);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CLOSED", "CANCELLED"})
    @DisplayName("assigning a terminal ticket returns 422 TICKET_NOT_EDITABLE and changes nothing (A-17)")
    void put_terminalTicket_isRejected(String status) {
        ApiResponse ticket = tickets.createInStatus(status);
        long id = ticket.number("id");

        ApiResponse response = api.put(TicketApi.assignee(id),
                json("version", ticket.number("version"), "assignee", "maria.lopez"));

        assertProblem(response, 422, "TICKET_NOT_EDITABLE");
        assertThat(response.string("currentStatus")).isEqualTo(status);
        assertThat(tickets.get(id).body()).isEqualTo(ticket.body());
    }

    @Test
    @DisplayName("a stale version returns 409 TICKET_CONCURRENT_MODIFICATION")
    void put_staleVersion_isRejected() {
        long id = tickets.create("Title", "Description").number("id");
        api.put(TicketApi.assignee(id), json("version", 0, "assignee", "maria.lopez"));

        ApiResponse response = api.put(TicketApi.assignee(id), json("version", 0, "assignee", "sam.ops"));

        assertProblem(response, 409, "TICKET_CONCURRENT_MODIFICATION");
        assertThat(tickets.get(id).string("assignee")).isEqualTo("maria.lopez");
    }

    @Test
    @DisplayName("status cannot be changed through the assignee endpoint (state-machine E3)")
    void put_withStatus_isRejected() {
        long id = tickets.create("Title", "Description").number("id");

        ApiResponse response = api.put(TicketApi.assignee(id),
                json("version", 0, "assignee", "maria.lopez", "status", "CLOSED"));

        assertValidationErrors(response, FieldError.body("status", "UNKNOWN_FIELD"));
        assertThat(tickets.get(id).string("status")).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("unknown ticket returns 404 TICKET_NOT_FOUND")
    void put_unknownTicket_returns404() {
        ApiResponse response = api.put(TicketApi.assignee(987_654_321_012L), json("version", 0, "assignee", "x"));

        assertProblem(response, 404, "TICKET_NOT_FOUND");
    }
}
