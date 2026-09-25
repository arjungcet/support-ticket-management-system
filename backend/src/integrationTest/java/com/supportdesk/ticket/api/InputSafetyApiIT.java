package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.encode;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * Security review M-4 — spec/api-contract.md §1.1 (text input, request size), §1.3 (precedence), §2.1
 * ({@code PAYLOAD_TOO_LARGE}), §2.2 ({@code INVALID_VALUE} for control characters). Runs on PostgreSQL, which rejects
 * NUL in text columns: before these rules a single {@code \u0000} produced a 500.
 */
@SpecAcceptanceTest
@DisplayName("M-4 Input safety: control characters and request size")
class InputSafetyApiIT extends ApiTestBase {

    private static final int LIMIT_BYTES = 128 * 1024;
    /** One code point (U+1F600) written as a JSON surrogate-pair escape: 12 bytes on the wire. */
    private static final String ESCAPED_EMOJI = "\\uD83D\\uDE00";

    @Nested
    @DisplayName("control characters")
    class ControlCharacters {

        @ParameterizedTest(name = "title containing U+{0}")
        @ValueSource(strings = {"0000", "0007", "000A", "0009", "001F"})
        void titleRejectsEveryControlCharacter(String hex) {
            String title = "Bad" + (char) Integer.parseInt(hex, 16) + "title";

            ApiResponse response = api.post(TicketApi.TICKETS, json("title", title, "description", "d"));

            assertValidationErrors(response, FieldError.body("title", "INVALID_VALUE"));
        }

        @Test
        void nulInADescriptionIsRejectedInsteadOfCausingAServerError() {
            ApiResponse response = api.post(TicketApi.TICKETS, json("title", "t", "description", "before\u0000after"));

            assertValidationErrors(response, FieldError.body("description", "INVALID_VALUE"));
        }

        @Test
        void multiLineFieldsKeepTabsAndLineBreaks() {
            String description = "Line one\n\tindented\r\nline three " + token;

            ApiResponse created = tickets.create(json("title", "Multi-line " + token, "description", description));

            assertThat(created.status()).as(created.rawBody()).isEqualTo(201);
            assertThat(tickets.get(created.number("id")).string("description")).isEqualTo(description);
            ApiResponse comment = tickets.addComment(created.number("id"), "agent", "First\nsecond\tline");
            assertThat(comment.status()).as(comment.rawBody()).isEqualTo(201);
            assertThat(comment.string("body")).isEqualTo("First\nsecond\tline");
        }

        @Test
        void allTextFieldsOfOneRequestAreReportedTogether() {
            ApiResponse response = api.post(TicketApi.TICKETS,
                    json("title", "a\u0000", "description", "b\u0001", "assignee", "c\nd"));

            assertValidationErrors(response,
                    FieldError.body("title", "INVALID_VALUE"),
                    FieldError.body("description", "INVALID_VALUE"),
                    FieldError.body("assignee", "INVALID_VALUE"));
        }

        @Test
        void updateAssignAndCommentRejectControlCharacters() {
            ApiResponse ticket = tickets.create("Target " + token, "d");
            long id = ticket.number("id");

            assertValidationErrors(api.patch(TicketApi.ticket(id), json("version", 0, "title", "x\u0000")),
                    FieldError.body("title", "INVALID_VALUE"));
            assertValidationErrors(api.put(TicketApi.assignee(id), json("version", 0, "assignee", "bob\u0000")),
                    FieldError.body("assignee", "INVALID_VALUE"));
            assertValidationErrors(api.post(TicketApi.comments(id), json("author", "a\tb", "body", "x\u0000")),
                    FieldError.body("author", "INVALID_VALUE"), FieldError.body("body", "INVALID_VALUE"));
            assertThat(tickets.get(id).number("version")).as("nothing changed").isZero();
        }

        @Test
        void searchKeywordWithANulIsAQueryValidationError() {
            ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + encode("abc\u0000"));

            assertValidationErrors(response, FieldError.query("q", "INVALID_VALUE"));
        }
    }

    @Nested
    @DisplayName("request size")
    class RequestSize {

        @Test
        void bodyOverTheLimitIsRejectedWith413() {
            String body = "{\"title\":\"t\",\"description\":\"" + "d".repeat(LIMIT_BYTES) + "\"}";

            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, body, MediaType.APPLICATION_JSON);

            assertProblem(response, 413, "PAYLOAD_TOO_LARGE");
        }

        @Test
        void oversizedCommentAndTransitionBodiesAreRejectedToo() {
            long id = tickets.create("Size " + token, "d").number("id");
            String padding = " ".repeat(LIMIT_BYTES);

            assertProblem(api.raw(HttpMethod.POST, TicketApi.comments(id),
                    "{\"author\":\"a\",\"body\":\"b\"}" + padding, MediaType.APPLICATION_JSON), 413, "PAYLOAD_TOO_LARGE");
            assertProblem(api.raw(HttpMethod.POST, TicketApi.transitions(id),
                    "{\"version\":0,\"targetStatus\":\"IN_PROGRESS\"}" + padding, MediaType.APPLICATION_JSON),
                    413, "PAYLOAD_TOO_LARGE");
            assertThat(tickets.get(id).string("status")).as("nothing changed").isEqualTo("OPEN");
        }

        @Test
        void bodyJustUnderTheLimitIsProcessedNormally() {
            String body = "{\"title\":\"t\",\"description\":\"" + "d".repeat(LIMIT_BYTES - 100) + "\"}";

            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, body, MediaType.APPLICATION_JSON);

            assertValidationErrors(response, FieldError.body("description", "TOO_LONG"));
        }

        @Test
        void theLargestValidRequestIsAccepted() {
            String body = "{\"title\":\"" + ESCAPED_EMOJI.repeat(200) + "\",\"description\":\""
                    + ESCAPED_EMOJI.repeat(5000) + "\",\"priority\":\"URGENT\",\"assignee\":\""
                    + ESCAPED_EMOJI.repeat(100) + "\"}";

            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, body, MediaType.APPLICATION_JSON);

            assertThat(response.status()).as(response.rawBody()).isEqualTo(201);
            assertThat(response.string("description").codePointCount(0, response.string("description").length()))
                    .isEqualTo(5000);
        }

        @Test
        void wrongContentTypeTakesPrecedenceOverSize() {
            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, "x".repeat(LIMIT_BYTES + 1),
                    MediaType.TEXT_PLAIN);

            assertProblem(response, 415, "UNSUPPORTED_MEDIA_TYPE");
        }
    }
}
