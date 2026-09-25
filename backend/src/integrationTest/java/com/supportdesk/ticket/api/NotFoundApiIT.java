package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.json;
import static com.supportdesk.support.ProblemAssert.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** TS-ERR-10 — every {ticketId} endpoint returns 404 TICKET_NOT_FOUND for a valid but unknown id. */
@SpecAcceptanceTest
@DisplayName("Not-found errors")
class NotFoundApiIT extends ApiTestBase {

    private static final long UNKNOWN_ID = 987_654_321_012L;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, TicketApi.ticket(UNKNOWN_ID), null),
                Arguments.of(HttpMethod.PATCH, TicketApi.ticket(UNKNOWN_ID), json("version", 0, "title", "x")),
                Arguments.of(HttpMethod.PUT, TicketApi.assignee(UNKNOWN_ID), json("version", 0, "assignee", "x")),
                Arguments.of(HttpMethod.POST, TicketApi.transitions(UNKNOWN_ID),
                        json("version", 0, "targetStatus", "IN_PROGRESS")),
                Arguments.of(HttpMethod.GET, TicketApi.comments(UNKNOWN_ID), null),
                Arguments.of(HttpMethod.POST, TicketApi.comments(UNKNOWN_ID), json("author", "a", "body", "b")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("endpoints")
    @DisplayName("unknown ticket → 404 TICKET_NOT_FOUND with ticketId and instance")
    void unknownTicket(HttpMethod method, String path, Map<String, Object> body) {
        String json = body == null ? null : JSON.writeValueAsString(body);

        ApiResponse response = api.exchange(method, path, json, MediaType.APPLICATION_JSON, HttpHeaders.EMPTY);

        assertProblem(response, 404, "TICKET_NOT_FOUND");
        assertThat(response.number("ticketId")).isEqualTo(UNKNOWN_ID);
        assertThat(response.string("instance")).isEqualTo(path);
    }

    @Test
    @DisplayName("an unknown route → 404 RESOURCE_NOT_FOUND")
    void unknownRoute() {
        assertProblem(api.get("/api/v1/does-not-exist"), 404, "RESOURCE_NOT_FOUND");
    }
}
