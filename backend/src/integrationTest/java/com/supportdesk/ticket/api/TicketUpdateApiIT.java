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

/**
 * REQ-4 (title, description, priority) — spec/api-contract.md §6.4, §1.2. Field-level validation boundaries live in
 * {@link BackendValidationApiIT}.
 */
@SpecAcceptanceTest
@DisplayName("REQ-4 Update ticket")
class TicketUpdateApiIT extends ApiTestBase {

    @Test
    @DisplayName("updates only the given field, bumps version and keeps the others")
    void patch_title_updatesOnlyTitle() {
        ApiResponse created = tickets.create(json(
                "title", "Old title", "description", "Keep me", "priority", "LOW", "assignee", "sam.ops"));
        long id = created.number("id");

        ApiResponse response = api.patch(TicketApi.ticket(id), json("version", 0, "title", "New title"));

        assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(200);
        assertThat(response.string("title")).isEqualTo("New title");
        assertThat(response.string("description")).isEqualTo("Keep me");
        assertThat(response.string("priority")).isEqualTo("LOW");
        assertThat(response.string("assignee")).isEqualTo("sam.ops");
        assertThat(response.string("status")).isEqualTo("OPEN");
        assertThat(response.number("version")).isEqualTo(1);
        assertThat(response.instant("updatedAt")).isAfterOrEqualTo(created.instant("updatedAt"));
        assertThat(response.instant("createdAt")).isEqualTo(created.instant("createdAt"));
    }

    @Test
    @DisplayName("updates description and priority together and persists the change")
    void patch_severalFields_persisted() {
        long id = tickets.create("Title", "Old description").number("id");

        api.patch(TicketApi.ticket(id), json("version", 0, "description", "New description", "priority", "URGENT"));
        ApiResponse fetched = tickets.get(id);

        assertThat(fetched.string("description")).isEqualTo("New description");
        assertThat(fetched.string("priority")).isEqualTo("URGENT");
        assertThat(fetched.number("version")).isEqualTo(1);
    }

    @Test
    @DisplayName("an update that changes nothing returns 200 with version and updatedAt unchanged")
    void patch_noActualChange_isNoOp() {
        ApiResponse created = tickets.create(json("title", "Same", "description", "Same", "priority", "HIGH"));
        long id = created.number("id");

        ApiResponse response = api.patch(TicketApi.ticket(id), json("version", 0, "title", "Same", "priority", "HIGH"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.number("version")).isZero();
        assertThat(response.instant("updatedAt")).isEqualTo(created.instant("updatedAt"));
    }

    @Test
    @DisplayName("a stale version returns 409 TICKET_CONCURRENT_MODIFICATION and changes nothing")
    void patch_staleVersion_isRejected() {
        long id = tickets.create("Title", "Description").number("id");
        api.patch(TicketApi.ticket(id), json("version", 0, "title", "First edit"));

        ApiResponse response = api.patch(TicketApi.ticket(id), json("version", 0, "title", "Second edit"));

        assertProblem(response, 409, "TICKET_CONCURRENT_MODIFICATION");
        assertThat(response.number("ticketId")).isEqualTo(id);
        ApiResponse fetched = tickets.get(id);
        assertThat(fetched.string("title")).isEqualTo("First edit");
        assertThat(fetched.number("version")).isEqualTo(1);
    }

    @Test
    @DisplayName("editing is allowed while RESOLVED (A-17)")
    void patch_resolvedTicket_isAllowed() {
        ApiResponse resolved = tickets.createInStatus("RESOLVED");

        ApiResponse response = api.patch(TicketApi.ticket(resolved.number("id")),
                json("version", resolved.number("version"), "title", "Clarified title"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.string("status")).isEqualTo("RESOLVED");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CLOSED", "CANCELLED"})
    @DisplayName("editing a terminal ticket returns 422 TICKET_NOT_EDITABLE and changes nothing (A-17)")
    void patch_terminalTicket_isRejected(String status) {
        ApiResponse ticket = tickets.createInStatus(status);
        long id = ticket.number("id");

        ApiResponse response = api.patch(TicketApi.ticket(id),
                json("version", ticket.number("version"), "title", "Too late"));

        assertProblem(response, 422, "TICKET_NOT_EDITABLE");
        assertThat(response.number("ticketId")).isEqualTo(id);
        assertThat(response.string("currentStatus")).isEqualTo(status);
        assertThat(tickets.get(id).body()).isEqualTo(ticket.body());
    }

    @Test
    @DisplayName("status cannot be changed through PATCH (state-machine E2)")
    void patch_withStatus_isRejectedAndStatusUnchanged() {
        ApiResponse closed = tickets.createInStatus("CLOSED");
        long id = closed.number("id");

        ApiResponse response = api.patch(TicketApi.ticket(id),
                json("version", closed.number("version"), "status", "OPEN"));

        assertValidationErrors(response, FieldError.body("status", "UNKNOWN_FIELD"));
        assertThat(tickets.get(id).string("status")).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("assignee cannot be changed through PATCH (use PUT /assignee)")
    void patch_withAssignee_isRejected() {
        long id = tickets.create("Title", "Description").number("id");

        ApiResponse response = api.patch(TicketApi.ticket(id), json("version", 0, "assignee", "sam.ops"));

        assertValidationErrors(response, FieldError.body("assignee", "UNKNOWN_FIELD"));
        assertThat(tickets.get(id).get("assignee")).isNull();
    }

    @Test
    @DisplayName("unknown ticket returns 404 TICKET_NOT_FOUND")
    void patch_unknownTicket_returns404() {
        ApiResponse response = api.patch(TicketApi.ticket(987_654_321_012L), json("version", 0, "title", "x"));

        assertProblem(response, 404, "TICKET_NOT_FOUND");
    }
}
