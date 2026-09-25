package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * REQ-2 — spec/api-contract.md §6.2, §4.2, §4.4. Lists are scoped with {@code q=<token>} so assertions only see tickets
 * this test created.
 */
@SpecAcceptanceTest
@DisplayName("REQ-2 List tickets")
class TicketListingApiIT extends ApiTestBase {

    private long createTokenTicket(String title, String priority) {
        return tickets.create(json("title", title, "description", "listing " + token, "priority", priority))
                .number("id");
    }

    @Test
    @DisplayName("list items are summaries: id, title, priority, status, assignee, createdAt, updatedAt only")
    void list_returnsSummaries() {
        long id = createTokenTicket("Summary shape", "HIGH");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.content()).hasSize(1);
        Map<String, Object> item = response.content().get(0);
        assertThat(item).containsOnlyKeys("id", "title", "priority", "status", "assignee", "createdAt", "updatedAt");
        assertThat(((Number) item.get("id")).longValue()).isEqualTo(id);
        assertThat(item).containsEntry("title", "Summary shape")
                .containsEntry("priority", "HIGH")
                .containsEntry("status", "OPEN")
                .containsEntry("assignee", null);
    }

    @Test
    @DisplayName("page metadata defaults to page 0, size 20")
    void list_defaultPageMetadata() {
        createTokenTicket("A", "LOW");
        createTokenTicket("B", "LOW");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token);

        assertThat(response.page()).containsEntry("number", 0).containsEntry("size", 20)
                .containsEntry("totalElements", 2).containsEntry("totalPages", 1);
    }

    @Test
    @DisplayName("default order is createdAt descending (newest first)")
    void list_defaultSort_isNewestFirst() {
        long first = createTokenTicket("First", "LOW");
        long second = createTokenTicket("Second", "LOW");
        long third = createTokenTicket("Third", "LOW");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token);

        assertThat(response.contentIds()).containsExactly(third, second, first);
    }

    @Test
    @DisplayName("sort=createdAt,asc returns oldest first")
    void list_sortCreatedAtAscending() {
        long first = createTokenTicket("First", "LOW");
        long second = createTokenTicket("Second", "LOW");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&sort=createdAt,asc");

        assertThat(response.contentIds()).containsExactly(first, second);
    }

    @Test
    @DisplayName("sort=priority,desc orders by rank URGENT > HIGH > MEDIUM > LOW, not alphabetically")
    void list_sortPriority_usesRank() {
        long low = createTokenTicket("low", "LOW");
        long urgent = createTokenTicket("urgent", "URGENT");
        long medium = createTokenTicket("medium", "MEDIUM");
        long high = createTokenTicket("high", "HIGH");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&sort=priority,desc");

        assertThat(response.contentIds()).containsExactly(urgent, high, medium, low);
    }

    @Test
    @DisplayName("sort=status,asc follows lifecycle order OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED")
    void list_sortStatus_usesLifecycleOrder() {
        long cancelled = tickets.createInStatus("CANCELLED", "c", "listing " + token).number("id");
        long resolved = tickets.createInStatus("RESOLVED", "r", "listing " + token).number("id");
        long open = tickets.createInStatus("OPEN", "o", "listing " + token).number("id");
        long closed = tickets.createInStatus("CLOSED", "cl", "listing " + token).number("id");
        long inProgress = tickets.createInStatus("IN_PROGRESS", "ip", "listing " + token).number("id");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&sort=status,asc");

        assertThat(response.contentIds()).containsExactly(open, inProgress, resolved, closed, cancelled);
    }

    @Test
    @DisplayName("pages are disjoint, complete and stable with correct totals")
    void list_paginatesStably() {
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(createTokenTicket("Paged " + i, "LOW"));
        }

        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&size=2&page=" + page);
            assertThat(response.page()).containsEntry("number", page).containsEntry("size", 2)
                    .containsEntry("totalElements", 5).containsEntry("totalPages", 3);
            seen.addAll(response.contentIds());
        }

        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(created);
    }

    @Test
    @DisplayName("a page beyond the end returns 200 with empty content and correct totals")
    void list_pageBeyondEnd_isEmpty() {
        createTokenTicket("Only one", "LOW");

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&page=5");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).containsEntry("number", 5).containsEntry("totalElements", 1)
                .containsEntry("totalPages", 1);
    }

    @Test
    @DisplayName("an empty result is 200 with content [] and zero totals, never 404")
    void list_noMatches_isEmptyPage() {
        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).containsEntry("totalElements", 0).containsEntry("totalPages", 0);
    }

    @Test
    @DisplayName("without parameters, newly created tickets appear in the list")
    void list_withoutParameters_includesNewTickets() {
        long id = createTokenTicket("Visible", "LOW");

        assertThat(tickets.listAllIds("")).contains(id);
    }
}
