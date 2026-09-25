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
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

/**
 * REQ-9 — spec/test-strategy.md §7 (TS-VAL-*), spec/api-contract.md §2.2, §2.3, §1.3.
 *
 * <p>Cases marked "(review SR-02)" / "(review SR-03)" encode contract behaviour that the specification review flagged
 * as hard to implement as written; they follow the current contract and may change with implementation plan STEP-02.
 */
@SpecAcceptanceTest
@DisplayName("REQ-9 Backend validation")
class BackendValidationApiIT extends ApiTestBase {

    private static final String MARKER = "ECHOCHECK";

    private static Map<String, Object> createBody(Object... overrides) {
        Map<String, Object> body = json("title", "Valid title", "description", "Valid description");
        for (int i = 0; i < overrides.length; i += 2) {
            if (overrides[i + 1] == Absent.VALUE) {
                body.remove((String) overrides[i]);
            } else {
                body.put((String) overrides[i], overrides[i + 1]);
            }
        }
        return body;
    }

    private enum Absent { VALUE }

    @Nested
    @DisplayName("create ticket body (TS-VAL-01…04, 08, 10, 11)")
    class CreateTicket {

        static Stream<Arguments> invalidBodies() {
            return Stream.of(
                    Arguments.of("title absent", createBody("title", Absent.VALUE), "title", "REQUIRED"),
                    Arguments.of("title null", createBody("title", null), "title", "REQUIRED"),
                    Arguments.of("title empty", createBody("title", ""), "title", "BLANK"),
                    Arguments.of("title whitespace", createBody("title", "   "), "title", "BLANK"),
                    Arguments.of("title 201 chars", createBody("title", "t".repeat(201)), "title", "TOO_LONG"),
                    Arguments.of("description absent", createBody("description", Absent.VALUE), "description",
                            "REQUIRED"),
                    Arguments.of("description null", createBody("description", null), "description", "REQUIRED"),
                    Arguments.of("description blank", createBody("description", " \t "), "description", "BLANK"),
                    Arguments.of("description 5001 chars", createBody("description", "d".repeat(5001)),
                            "description", "TOO_LONG"),
                    Arguments.of("priority unknown", createBody("priority", "CRITICAL"), "priority",
                            "INVALID_VALUE"),
                    Arguments.of("priority lower-case", createBody("priority", "high"), "priority",
                            "INVALID_VALUE"),
                    Arguments.of("assignee 101 chars", createBody("assignee", "a".repeat(101)), "assignee",
                            "TOO_LONG"),
                    Arguments.of("id supplied", createBody("id", 5), "id", "UNKNOWN_FIELD"),
                    Arguments.of("status supplied", createBody("status", "OPEN"), "status", "UNKNOWN_FIELD"),
                    Arguments.of("createdAt supplied", createBody("createdAt", "2026-01-01T00:00:00Z"), "createdAt",
                            "UNKNOWN_FIELD"),
                    Arguments.of("version supplied", createBody("version", 0), "version", "UNKNOWN_FIELD"),
                    Arguments.of("unknown property", createBody("foo", "bar"), "foo", "UNKNOWN_FIELD"));
        }

        @ParameterizedTest(name = "{0} → {3}")
        @MethodSource("invalidBodies")
        @DisplayName("invalid create bodies are rejected with the exact field error")
        void invalid(String caseName, Map<String, Object> body, String field, String code) {
            ApiResponse response = api.post(TicketApi.TICKETS, body);

            assertValidationErrors(response, FieldError.body(field, code));
        }

        static Stream<Arguments> boundaryValuesAccepted() {
            return Stream.of(
                    Arguments.of("title 1 char", createBody("title", "t")),
                    Arguments.of("title 200 chars", createBody("title", "t".repeat(200))),
                    Arguments.of("title 200 chars padded with spaces", createBody("title", "  " + "t".repeat(200) + "  ")),
                    Arguments.of("title 200 CJK chars", createBody("title", "票".repeat(200))),
                    Arguments.of("description 5000 chars", createBody("description", "d".repeat(5000))),
                    Arguments.of("assignee 100 chars", createBody("assignee", "a".repeat(100))),
                    Arguments.of("priority omitted", createBody("priority", Absent.VALUE)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("boundaryValuesAccepted")
        @DisplayName("boundary values inside the limits are accepted")
        void accepted(String caseName, Map<String, Object> body) {
            ApiResponse response = api.post(TicketApi.TICKETS, body);

            assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(201);
        }

        @Test
        @DisplayName("TS-VAL-10 all invalid fields are reported together")
        void multipleErrors_reportedTogether() {
            ApiResponse response = api.post(TicketApi.TICKETS, json(
                    "title", "  ", "description", "d".repeat(5001), "priority", "CRITICAL"));

            assertValidationErrors(response,
                    FieldError.body("title", "BLANK"),
                    FieldError.body("description", "TOO_LONG"),
                    FieldError.body("priority", "INVALID_VALUE"));
        }

        @Test
        @DisplayName("TS-ERR-08 the submitted value is never echoed back")
        void rejectedValue_notEchoed() {
            ApiResponse response = api.post(TicketApi.TICKETS,
                    createBody("title", MARKER + "x".repeat(250)));

            assertValidationErrors(response, FieldError.body("title", "TOO_LONG"));
            assertThat(response.rawBody()).doesNotContain(MARKER);
        }
    }

    @Nested
    @DisplayName("update ticket body (TS-VAL-01…03, 06, 09)")
    class UpdateTicket {

        @Test
        @DisplayName("version is required")
        void versionMissing() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.patch(TicketApi.ticket(id), json("title", "New")),
                    FieldError.body("version", "REQUIRED"));
        }

        @Test
        @DisplayName("negative version is INVALID_VALUE")
        void versionNegative() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.patch(TicketApi.ticket(id), json("version", -1, "title", "New")),
                    FieldError.body("version", "INVALID_VALUE"));
        }

        @Test
        @DisplayName("TS-VAL-09 a body with only version is NO_CHANGES_REQUESTED (object-level, field null)")
        void noFields() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.patch(TicketApi.ticket(id), json("version", 0)),
                    new FieldError("body", null, "NO_CHANGES_REQUESTED"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"title", "description", "priority"})
        @DisplayName("explicit null for an editable field is REQUIRED (review SR-02)")
        void explicitNull(String field) {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.patch(TicketApi.ticket(id), json("version", 0, field, null)),
                    FieldError.body(field, "REQUIRED"));
        }

        @Test
        @DisplayName("blank title and too-long description are rejected")
        void blankAndTooLong() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.patch(TicketApi.ticket(id),
                            json("version", 0, "title", " ", "description", "d".repeat(5001))),
                    FieldError.body("title", "BLANK"), FieldError.body("description", "TOO_LONG"));
        }

        @Test
        @DisplayName("an invalid body is rejected before the version or business rules are checked")
        void invalidBody_leavesTicketUnchanged() {
            ApiResponse ticket = tickets.create("Original", "d");

            api.patch(TicketApi.ticket(ticket.number("id")), json("version", 0, "title", "", "priority", "HIGH"));

            assertThat(tickets.get(ticket.number("id")).body()).isEqualTo(ticket.body());
        }
    }

    @Nested
    @DisplayName("assignee body (TS-VAL-04, 06)")
    class Assignee {

        @Test
        @DisplayName("assignee longer than 100 characters is TOO_LONG")
        void tooLong() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.put(TicketApi.assignee(id), json("version", 0, "assignee", "a".repeat(101))),
                    FieldError.body("assignee", "TOO_LONG"));
        }

        @Test
        @DisplayName("the assignee key is required (API-3, review SR-02)")
        void keyMissing() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.put(TicketApi.assignee(id), json("version", 0)),
                    FieldError.body("assignee", "REQUIRED"));
        }

        @Test
        @DisplayName("version is required")
        void versionMissing() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.put(TicketApi.assignee(id), json("assignee", "sam.ops")),
                    FieldError.body("version", "REQUIRED"));
        }
    }

    @Nested
    @DisplayName("comment body (TS-VAL-05)")
    class Comment {

        static Stream<Arguments> invalidComments() {
            return Stream.of(
                    Arguments.of("author absent", json("body", "b"), "author", "REQUIRED"),
                    Arguments.of("author null", json("author", null, "body", "b"), "author", "REQUIRED"),
                    Arguments.of("author blank", json("author", "  ", "body", "b"), "author", "BLANK"),
                    Arguments.of("author 101 chars", json("author", "a".repeat(101), "body", "b"), "author",
                            "TOO_LONG"),
                    Arguments.of("body absent", json("author", "a"), "body", "REQUIRED"),
                    Arguments.of("body null", json("author", "a", "body", null), "body", "REQUIRED"),
                    Arguments.of("body blank", json("author", "a", "body", "\n "), "body", "BLANK"),
                    Arguments.of("body 5001 chars", json("author", "a", "body", "b".repeat(5001)), "body",
                            "TOO_LONG"),
                    Arguments.of("unknown property", json("author", "a", "body", "b", "ticketId", 1), "ticketId",
                            "UNKNOWN_FIELD"));
        }

        @ParameterizedTest(name = "{0} → {3}")
        @MethodSource("invalidComments")
        @DisplayName("invalid comment bodies are rejected with the exact field error")
        void invalid(String caseName, Map<String, Object> body, String field, String code) {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.post(TicketApi.comments(id), body), FieldError.body(field, code));
        }

        @Test
        @DisplayName("boundary values 100 / 5000 are accepted")
        void boundariesAccepted() {
            long id = tickets.create("t", "d").number("id");

            ApiResponse response = api.post(TicketApi.comments(id),
                    json("author", "a".repeat(100), "body", "b".repeat(5000)));

            assertThat(response.status()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("path and query parameters (TS-VAL-20…25)")
    class Parameters {

        @ParameterizedTest(name = "ticketId={0}")
        @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808"})
        @DisplayName("TS-VAL-20 invalid ticketId is a path INVALID_VALUE")
        void invalidTicketId(String ticketId) {
            assertValidationErrors(api.get(TicketApi.TICKETS + "/" + ticketId),
                    FieldError.path("ticketId", "INVALID_VALUE"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"page=-1", "page=x", "size=0", "size=101", "size=x"})
        @DisplayName("TS-VAL-21/22 invalid paging parameters are rejected")
        void invalidPaging(String query) {
            String parameter = query.substring(0, query.indexOf('='));

            assertValidationErrors(api.get(TicketApi.TICKETS + "?" + query),
                    FieldError.query(parameter, "INVALID_VALUE"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"page=0", "size=1", "size=100"})
        @DisplayName("TS-VAL-21/22 paging boundaries are accepted")
        void validPaging(String query) {
            assertThat(api.get(TicketApi.TICKETS + "?" + query).status()).isEqualTo(200);
        }

        @ParameterizedTest(name = "sort={0}")
        @ValueSource(strings = {"title,asc", "createdAt,up", ",desc", "description,desc"})
        @DisplayName("TS-VAL-23 sort outside the allow-list is rejected")
        void invalidSort(String sort) {
            assertValidationErrors(api.get(TicketApi.TICKETS + "?sort=" + encode(sort)),
                    FieldError.query("sort", "INVALID_VALUE"));
        }

        @ParameterizedTest(name = "sort={0}")
        @ValueSource(strings = {"createdAt,desc", "updatedAt,asc", "priority", "status,desc"})
        @DisplayName("TS-VAL-23 allowed sort values are accepted")
        void validSort(String sort) {
            assertThat(api.get(TicketApi.TICKETS + "?sort=" + encode(sort)).status()).isEqualTo(200);
        }

        @Test
        @DisplayName("TS-VAL-24 q longer than 100 characters is TOO_LONG")
        void keywordTooLong() {
            assertValidationErrors(api.get(TicketApi.TICKETS + "?q=" + "k".repeat(101)),
                    FieldError.query("q", "TOO_LONG"));
        }

        @Test
        @DisplayName("comment paging parameters are validated too")
        void commentPaging() {
            long id = tickets.create("t", "d").number("id");

            assertValidationErrors(api.get(TicketApi.comments(id) + "?size=101"),
                    FieldError.query("size", "INVALID_VALUE"));
        }
    }

    @Nested
    @DisplayName("malformed requests (TS-VAL-30…32)")
    class Malformed {

        @ParameterizedTest(name = "body: {0}")
        @ValueSource(strings = {"{\"title\":", "[\"title\"]", "not json", "{}}"})
        @DisplayName("TS-VAL-30 unparseable JSON or a non-object is MALFORMED_REQUEST")
        void unparseable(String body) {
            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, body, MediaType.APPLICATION_JSON);

            assertProblem(response, 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("TS-VAL-31 a string field sent as a number is MALFORMED_REQUEST (review SR-03)")
        void numberForString() {
            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS,
                    "{\"title\":123,\"description\":\"d\"}", MediaType.APPLICATION_JSON);

            assertProblem(response, 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("TS-VAL-31 version sent as a string is MALFORMED_REQUEST (review SR-03)")
        void stringForNumber() {
            long id = tickets.create("t", "d").number("id");

            ApiResponse response = api.raw(HttpMethod.PATCH, TicketApi.ticket(id),
                    "{\"version\":\"0\",\"title\":\"x\"}", MediaType.APPLICATION_JSON);

            assertProblem(response, 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("TS-VAL-32 a non-JSON content type is 415 UNSUPPORTED_MEDIA_TYPE")
        void wrongContentType() {
            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, "title=x", MediaType.TEXT_PLAIN);

            assertProblem(response, 415, "UNSUPPORTED_MEDIA_TYPE");
        }
    }

    @Nested
    @DisplayName("error precedence (TS-ERR-05, api-contract §1.3)")
    class Precedence {

        @Test
        @DisplayName("an invalid body on an unknown ticket is 400, not 404")
        void validationBeforeNotFound() {
            ApiResponse response = api.patch(TicketApi.ticket(987_654_321_012L), json("version", 0, "title", ""));

            assertValidationErrors(response, FieldError.body("title", "BLANK"));
        }

        @Test
        @DisplayName("a malformed body is 400 MALFORMED_REQUEST even when fields would also be invalid")
        void malformedBeforeValidation() {
            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, "{\"title\": \"\",",
                    MediaType.APPLICATION_JSON);

            assertProblem(response, 400, "MALFORMED_REQUEST");
        }

        @Test
        @DisplayName("a wrong content type is 415 even when the body is also malformed")
        void mediaTypeBeforeMalformed() {
            ApiResponse response = api.raw(HttpMethod.POST, TicketApi.TICKETS, "{\"title\":", MediaType.TEXT_PLAIN);

            assertProblem(response, 415, "UNSUPPORTED_MEDIA_TYPE");
        }

        @Test
        @DisplayName("a stale version on a terminal ticket is 409 CONCURRENT_MODIFICATION, not 422")
        void versionBeforeBusinessRule() {
            long id = tickets.createInStatus("CLOSED").number("id");

            ApiResponse response = api.patch(TicketApi.ticket(id), json("version", 0, "title", "x"));

            assertProblem(response, 409, "TICKET_CONCURRENT_MODIFICATION");
        }
    }
}
