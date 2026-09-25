package com.supportdesk.ticket.api;

import static com.supportdesk.support.ProblemAssert.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** TS-ERR-02/04, TS-WEB-06, TS-INT-06 — the single error structure and correlation ids (spec/api-contract.md §1.1, §2). */
@SpecAcceptanceTest
@DisplayName("API error contract")
class ErrorContractApiIT extends ApiTestBase {

    private static final long UNKNOWN_ID = 987_654_321_012L;

    private ApiResponse getWithCorrelationId(String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-Id", correlationId);
        return api.exchange(HttpMethod.GET, TicketApi.ticket(UNKNOWN_ID), null, null, headers);
    }

    @Test
    @DisplayName("errors carry every required property, a URI type, the request path and a parseable timestamp")
    void problemShape() {
        ApiResponse response = api.get(TicketApi.ticket(UNKNOWN_ID));

        assertProblem(response, 404, "TICKET_NOT_FOUND");
        assertThat(response.string("type")).startsWith("https://");
        assertThat(response.string("title")).isNotBlank();
        assertThat(response.string("detail")).isNotBlank();
        assertThat(response.string("instance")).isEqualTo(TicketApi.ticket(UNKNOWN_ID));
        assertThat(Instant.parse(response.string("timestamp"))).isNotNull();
    }

    @Test
    @DisplayName("a valid client X-Correlation-Id is echoed in the header and the body")
    void correlationId_isEchoed() {
        ApiResponse response = getWithCorrelationId("abc-123-DEF");

        assertThat(response.header("X-Correlation-Id")).isEqualTo("abc-123-DEF");
        assertThat(response.string("correlationId")).isEqualTo("abc-123-DEF");
    }

    @Test
    @DisplayName("without X-Correlation-Id the server generates one and returns it")
    void correlationId_isGenerated() {
        ApiResponse response = api.get(TicketApi.ticket(UNKNOWN_ID));

        assertThat(response.header("X-Correlation-Id")).isNotBlank();
        assertThat(response.string("correlationId")).isEqualTo(response.header("X-Correlation-Id"));
    }

    @ParameterizedTest(name = "X-Correlation-Id=\"{0}\"")
    @ValueSource(strings = {"has spaces", "semi;colon", "0123456789012345678901234567890123456789012345678901234567890123x"})
    @DisplayName("an invalid X-Correlation-Id is replaced by a generated one")
    void correlationId_invalidIsReplaced(String invalid) {
        ApiResponse response = getWithCorrelationId(invalid);

        assertThat(response.header("X-Correlation-Id")).isNotBlank().isNotEqualTo(invalid);
        assertThat(response.string("correlationId")).isEqualTo(response.header("X-Correlation-Id"));
    }

    @Test
    @DisplayName("successful responses also carry X-Correlation-Id")
    void correlationId_onSuccess() {
        ApiResponse response = api.get(TicketApi.TICKETS);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("X-Correlation-Id")).isNotBlank();
    }

    @Test
    @DisplayName("a method not supported on an existing route → 405 METHOD_NOT_ALLOWED with an Allow header")
    void methodNotAllowed() {
        long id = tickets.create("t", "d").number("id");

        ApiResponse response = api.exchange(HttpMethod.DELETE, TicketApi.ticket(id), null, null, HttpHeaders.EMPTY);

        assertProblem(response, 405, "METHOD_NOT_ALLOWED");
        assertThat(response.header("Allow")).isNotBlank();
    }
}
