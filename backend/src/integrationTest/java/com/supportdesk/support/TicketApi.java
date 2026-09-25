package com.supportdesk.support;

import static com.supportdesk.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test fixtures that drive the ticket API. Tickets in a given state are always reached by walking legal transitions
 * through the API — never by writing the database directly — so fixtures cannot create impossible states
 * (spec/test-strategy.md §5).
 */
public final class TicketApi {

    public static final String TICKETS = "/api/v1/tickets";

    /** Legal path from OPEN to each status (spec/state-machine.md §3). */
    private static final Map<String, List<String>> PATH_FROM_OPEN = Map.of(
            "OPEN", List.of(),
            "IN_PROGRESS", List.of("IN_PROGRESS"),
            "RESOLVED", List.of("IN_PROGRESS", "RESOLVED"),
            "CLOSED", List.of("IN_PROGRESS", "RESOLVED", "CLOSED"),
            "CANCELLED", List.of("CANCELLED"));

    private final ApiClient api;

    public TicketApi(ApiClient api) {
        this.api = api;
    }

    public static String ticket(long id) {
        return TICKETS + "/" + id;
    }

    public static String transitions(long id) {
        return ticket(id) + "/status-transitions";
    }

    public static String assignee(long id) {
        return ticket(id) + "/assignee";
    }

    public static String comments(long id) {
        return ticket(id) + "/comments";
    }

    public ApiResponse create(String title, String description) {
        return created(api.post(TICKETS, json("title", title, "description", description)));
    }

    public ApiResponse create(Map<String, Object> body) {
        return created(api.post(TICKETS, body));
    }

    public ApiResponse get(long id) {
        ApiResponse response = api.get(ticket(id));
        assertThat(response.status()).as("fixture GET %s; body=%s", id, response.rawBody()).isEqualTo(200);
        return response;
    }

    public ApiResponse transition(long id, long version, String targetStatus) {
        return api.post(transitions(id), json("version", version, "targetStatus", targetStatus));
    }

    /** Creates a ticket and walks it to {@code status} via legal transitions; returns the final representation. */
    public ApiResponse createInStatus(String status, String title, String description) {
        return createInStatus(status, json("title", title, "description", description));
    }

    /** Creates a ticket from {@code body} and walks it to {@code status} via legal transitions. */
    public ApiResponse createInStatus(String status, Map<String, Object> body) {
        ApiResponse current = create(body);
        for (String next : PATH_FROM_OPEN.get(status)) {
            ApiResponse moved = transition(current.number("id"), current.number("version"), next);
            assertThat(moved.status()).as("fixture transition to %s; body=%s", next, moved.rawBody()).isEqualTo(200);
            current = moved;
        }
        assertThat(current.string("status")).isEqualTo(status);
        return current;
    }

    public ApiResponse createInStatus(String status) {
        return createInStatus(status, "Ticket in " + status, "Created for a state-machine test");
    }

    public ApiResponse addComment(long ticketId, String author, String body) {
        ApiResponse response = api.post(comments(ticketId), json("author", author, "body", body));
        assertThat(response.status()).as("fixture add comment; body=%s", response.rawBody()).isEqualTo(201);
        return response;
    }

    /** Collects ids across all pages of a list query (query string without page/size). */
    public List<Long> listAllIds(String query) {
        List<Long> ids = new ArrayList<>();
        String separator = query.isEmpty() ? "?" : "&";
        for (int page = 0; ; page++) {
            ApiResponse response = api.get(TICKETS + query + separator + "size=100&page=" + page);
            assertThat(response.status()).as("list %s; body=%s", query, response.rawBody()).isEqualTo(200);
            ids.addAll(response.contentIds());
            long totalPages = ((Number) response.page().get("totalPages")).longValue();
            if (page + 1 >= totalPages) {
                return ids;
            }
        }
    }

    private static ApiResponse created(ApiResponse response) {
        assertThat(response.status()).as("fixture create ticket; body=%s", response.rawBody()).isEqualTo(201);
        return response;
    }
}
