package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** REQ-1 — spec/api-contract.md §6.1, §4.1. Validation boundaries live in {@link BackendValidationApiIT}. */
@SpecAcceptanceTest
@DisplayName("REQ-1 Create ticket")
class TicketCreationApiIT extends ApiTestBase {

    @Test
    @DisplayName("creates an OPEN ticket and returns 201, a relative Location and the full representation")
    void create_returnsCreatedTicket() {
        // When
        ApiResponse response = api.post(TicketApi.TICKETS, json(
                "title", "Cannot log in to portal",
                "description", "User reports a 403 after password reset.",
                "priority", "HIGH"));

        // Then
        assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(201);
        assertThat(response.contentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        long id = response.number("id");
        assertThat(id).isPositive();
        assertThat(response.header("Location")).isEqualTo("/api/v1/tickets/" + id);
        assertThat(response.body()).containsOnlyKeys(
                "id", "title", "description", "priority", "status", "assignee", "createdAt", "updatedAt",
                "resolvedAt", "closedAt", "cancelledAt", "version", "allowedTransitions");
        assertThat(response.string("title")).isEqualTo("Cannot log in to portal");
        assertThat(response.string("description")).isEqualTo("User reports a 403 after password reset.");
        assertThat(response.string("priority")).isEqualTo("HIGH");
        assertThat(response.string("status")).isEqualTo("OPEN");
        assertThat(response.get("assignee")).isNull();
        assertThat(response.get("resolvedAt")).isNull();
        assertThat(response.get("closedAt")).isNull();
        assertThat(response.get("cancelledAt")).isNull();
        assertThat(response.number("version")).isZero();
        assertThat(response.list("allowedTransitions")).containsExactly("IN_PROGRESS", "CANCELLED");
        assertThat(response.instant("createdAt")).isNotNull().isEqualTo(response.instant("updatedAt"));
    }

    @Test
    @DisplayName("priority defaults to MEDIUM when omitted")
    void create_withoutPriority_defaultsToMedium() {
        ApiResponse response = tickets.create("Printer jam", "Paper stuck in tray 2");

        assertThat(response.string("priority")).isEqualTo("MEDIUM");
    }

    @ParameterizedTest(name = "priority {0}")
    @ValueSource(strings = {"LOW", "MEDIUM", "HIGH", "URGENT"})
    @DisplayName("accepts every priority value")
    void create_acceptsEveryPriority(String priority) {
        ApiResponse response = tickets.create(json("title", "t", "description", "d", "priority", priority));

        assertThat(response.string("priority")).isEqualTo(priority);
    }

    @Test
    @DisplayName("stores the assignee when given")
    void create_withAssignee_storesIt() {
        ApiResponse response = tickets.create(json(
                "title", "VPN down", "description", "No tunnel", "assignee", "maria.lopez"));

        assertThat(response.string("assignee")).isEqualTo("maria.lopez");
    }

    @ParameterizedTest(name = "assignee \"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank assignee means unassigned (DM-4)")
    void create_withBlankAssignee_isUnassigned(String assignee) {
        ApiResponse response = tickets.create(json(
                "title", "VPN down", "description", "No tunnel", "assignee", assignee));

        assertThat(response.get("assignee")).isNull();
    }

    @Test
    @DisplayName("trims leading and trailing whitespace but keeps inner whitespace (A-30)")
    void create_trimsText() {
        ApiResponse response = tickets.create(json(
                "title", "   Printer   jam  ",
                "description", "\n Paper stuck \t",
                "assignee", "  maria.lopez "));

        assertThat(response.string("title")).isEqualTo("Printer   jam");
        assertThat(response.string("description")).isEqualTo("Paper stuck");
        assertThat(response.string("assignee")).isEqualTo("maria.lopez");
    }

    @Test
    @DisplayName("the created ticket is persisted and retrievable with the same representation")
    void create_isRetrievable() {
        ApiResponse created = tickets.create("Printer jam", "Paper stuck in tray 2");

        ApiResponse fetched = tickets.get(created.number("id"));

        assertThat(fetched.body()).isEqualTo(created.body());
    }

    @Test
    @DisplayName("each created ticket gets a new id")
    void create_assignsDistinctIds() {
        long first = tickets.create("One", "First").number("id");
        long second = tickets.create("Two", "Second").number("id");

        assertThat(second).isNotEqualTo(first);
    }
}
