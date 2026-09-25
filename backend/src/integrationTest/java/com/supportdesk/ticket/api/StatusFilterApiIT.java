package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static com.supportdesk.support.ProblemAssert.assertValidationErrors;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.ProblemAssert.FieldError;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * REQ-7 — spec/api-contract.md §6.2.2, spec/test-strategy.md §10 (TS-FILT-01…10). Results are scoped with
 * {@code q=<token>} so totals are exact.
 */
@SpecAcceptanceTest
@DisplayName("REQ-7 Filter tickets by status")
class StatusFilterApiIT extends ApiTestBase {

    private final Map<String, Long> seeded = new LinkedHashMap<>();

    @BeforeEach
    void seed() {
        seed("1", "OPEN", "LOW");
        seed("2", "IN_PROGRESS", "HIGH");
        seed("3", "RESOLVED", "MEDIUM");
        seed("4", "CLOSED", "URGENT");
        seed("5", "CANCELLED", "LOW");
        seed("6", "OPEN", "URGENT");
        seed("7", "OPEN", "MEDIUM");
    }

    private void seed(String label, String status, String priority) {
        ApiResponse ticket = tickets.createInStatus(status, json(
                "title", "Filter " + label, "description", "filter " + token, "priority", priority));
        seeded.put(label, ticket.number("id"));
    }

    private List<Long> ids(String... labels) {
        return Arrays.stream(labels).map(seeded::get).toList();
    }

    private ApiResponse list(String query) {
        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&size=100" + query);
        assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(200);
        return response;
    }

    @Test
    @DisplayName("TS-FILT-01 status=OPEN returns only OPEN tickets")
    void open_returnsOpenOnly() {
        assertThat(list("&status=OPEN").contentIds()).containsExactlyInAnyOrderElementsOf(ids("1", "6", "7"));
    }

    @ParameterizedTest(name = "status={0}")
    @CsvSource({"IN_PROGRESS,2", "RESOLVED,3", "CLOSED,4", "CANCELLED,5"})
    @DisplayName("TS-FILT-02 each status returns exactly the tickets in that status")
    void eachStatus_returnsMatchingTickets(String status, String label) {
        ApiResponse response = list("&status=" + status);

        assertThat(response.contentIds()).containsExactly(seeded.get(label));
        response.content().forEach(item -> assertThat(item).containsEntry("status", status));
    }

    @Test
    @DisplayName("TS-FILT-03 repeated status parameters combine with OR")
    void repeatedParameters_combineWithOr() {
        assertThat(list("&status=OPEN&status=IN_PROGRESS").contentIds())
                .containsExactlyInAnyOrderElementsOf(ids("1", "2", "6", "7"));
    }

    @Test
    @DisplayName("TS-FILT-04 comma-separated statuses behave like repeated parameters")
    void commaSeparated_equalsRepeated() {
        assertThat(list("&status=OPEN,IN_PROGRESS").contentIds())
                .containsExactlyInAnyOrderElementsOf(ids("1", "2", "6", "7"));
    }

    @Test
    @DisplayName("TS-FILT-05 duplicate statuses are ignored")
    void duplicates_areIgnored() {
        ApiResponse response = list("&status=OPEN&status=OPEN");

        assertThat(response.contentIds()).containsExactlyInAnyOrderElementsOf(ids("1", "6", "7"));
        assertThat(response.page()).containsEntry("totalElements", 3);
    }

    @Test
    @DisplayName("TS-FILT-06 without a status filter all statuses are returned, including terminal ones (API-2)")
    void noFilter_includesTerminal() {
        ApiResponse response = list("");

        assertThat(response.contentIds()).containsExactlyInAnyOrderElementsOf(seeded.values());
        assertThat(response.page()).containsEntry("totalElements", 7);
    }

    @Test
    @DisplayName("TS-FILT-07 a status with no tickets returns 200 and an empty page")
    void statusWithoutTickets_isEmptyPage() {
        String otherToken = token + "vv";
        tickets.create("Only open", "filter " + otherToken);

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + otherToken + "&status=CLOSED");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).containsEntry("totalElements", 0).containsEntry("totalPages", 0);
    }

    @ParameterizedTest(name = "status={0}")
    @ValueSource(strings = {"FOO", "open", "REOPENED"})
    @DisplayName("TS-FILT-08 an unknown or wrongly-cased status is rejected")
    void unknownStatus_isRejected(String status) {
        ApiResponse response = api.get(TicketApi.TICKETS + "?status=" + status);

        assertValidationErrors(response, FieldError.query("status", "INVALID_VALUE"));
    }

    @Test
    @DisplayName("TS-FILT-09 filter combines with priority sort and paging")
    void filter_withSortAndPaging() {
        ApiResponse page0 = api.get(TicketApi.TICKETS + "?q=" + token
                + "&status=OPEN&status=IN_PROGRESS&sort=priority,desc&size=2&page=0");
        ApiResponse page1 = api.get(TicketApi.TICKETS + "?q=" + token
                + "&status=OPEN&status=IN_PROGRESS&sort=priority,desc&size=2&page=1");

        // OPEN/IN_PROGRESS tickets: 6 URGENT, 2 HIGH, 7 MEDIUM, 1 LOW
        assertThat(page0.contentIds()).containsExactlyElementsOf(ids("6", "2"));
        assertThat(page1.contentIds()).containsExactlyElementsOf(ids("7", "1"));
        assertThat(page0.page()).containsEntry("totalElements", 4).containsEntry("totalPages", 2);
    }

    @Test
    @DisplayName("TS-FILT-10 after a status change the ticket appears under the new status only")
    void statusChange_movesTicketBetweenFilters() {
        long id = seeded.get("1");
        ApiResponse ticket = tickets.get(id);
        tickets.transition(id, ticket.number("version"), "IN_PROGRESS");

        assertThat(list("&status=OPEN").contentIds()).doesNotContain(id);
        assertThat(list("&status=IN_PROGRESS").contentIds()).contains(id);
    }
}
