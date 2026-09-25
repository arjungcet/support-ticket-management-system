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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * REQ-11, REQ-12 — spec/state-machine.md, spec/api-contract.md §6.9, spec/test-strategy.md §6.
 *
 * <p>The backend must enforce the state machine for any HTTP client; these tests are exactly such a client.
 * "Unchanged" is verified by re-reading the ticket through the API (the black-box equivalent of re-reading the row).
 */
@SpecAcceptanceTest
@DisplayName("REQ-11/12 Ticket state machine")
class StatusTransitionApiIT extends ApiTestBase {

    /** Allowed targets per state, in contract order (spec/state-machine.md §4 compact table). */
    private static final Map<String, List<String>> ALLOWED = Map.of(
            "OPEN", List.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", List.of("RESOLVED", "CANCELLED"),
            "RESOLVED", List.of("CLOSED"),
            "CLOSED", List.of(),
            "CANCELLED", List.of());

    private ApiResponse transition(ApiResponse ticket, String target) {
        return tickets.transition(ticket.number("id"), ticket.number("version"), target);
    }

    /** Asserts a 200 transition result and that the same state is returned by a subsequent GET. */
    private ApiResponse assertTransitioned(ApiResponse before, ApiResponse response, String target) {
        assertThat(response.status()).as("body=%s", response.rawBody()).isEqualTo(200);
        assertThat(response.string("status")).isEqualTo(target);
        assertThat(response.number("version")).isEqualTo(before.number("version") + 1);
        assertThat(response.instant("updatedAt")).isAfterOrEqualTo(before.instant("updatedAt"));
        assertThat(response.list("allowedTransitions")).containsExactlyElementsOf(ALLOWED.get(target));
        assertThat(response.string("title")).isEqualTo(before.string("title"));
        assertThat(response.instant("createdAt")).isEqualTo(before.instant("createdAt"));
        assertThat(tickets.get(response.number("id")).body()).isEqualTo(response.body());
        return response;
    }

    /** Asserts a 409 invalid-transition problem and that the ticket is unchanged. */
    private void assertRejected(ApiResponse before, ApiResponse response, String target) {
        String current = before.string("status");
        assertProblem(response, 409, "TICKET_INVALID_TRANSITION");
        assertThat(response.number("ticketId")).isEqualTo(before.number("id"));
        assertThat(response.string("currentStatus")).isEqualTo(current);
        assertThat(response.string("targetStatus")).isEqualTo(target);
        assertThat(response.list("allowedTransitions")).containsExactlyElementsOf(ALLOWED.get(current));
        assertThat(tickets.get(before.number("id")).body())
                .as("ticket unchanged after rejected %s → %s", current, target)
                .isEqualTo(before.body());
    }

    @Nested
    @DisplayName("allowed transitions")
    class Allowed {

        @Test
        @DisplayName("TS-SM-A1 OPEN → IN_PROGRESS is allowed; no lifecycle timestamp is set")
        void openToInProgress() {
            ApiResponse before = tickets.createInStatus("OPEN");

            ApiResponse after = assertTransitioned(before, transition(before, "IN_PROGRESS"), "IN_PROGRESS");

            assertThat(after.body()).containsEntry("resolvedAt", null).containsEntry("closedAt", null)
                    .containsEntry("cancelledAt", null);
        }

        @Test
        @DisplayName("TS-SM-A2 IN_PROGRESS → RESOLVED is allowed and sets resolvedAt")
        void inProgressToResolved() {
            ApiResponse before = tickets.createInStatus("IN_PROGRESS");

            ApiResponse after = assertTransitioned(before, transition(before, "RESOLVED"), "RESOLVED");

            assertThat(after.instant("resolvedAt")).isEqualTo(after.instant("updatedAt"));
            assertThat(after.body()).containsEntry("closedAt", null).containsEntry("cancelledAt", null);
        }

        @Test
        @DisplayName("TS-SM-A3 RESOLVED → CLOSED is allowed, sets closedAt and keeps resolvedAt")
        void resolvedToClosed() {
            ApiResponse before = tickets.createInStatus("RESOLVED");

            ApiResponse after = assertTransitioned(before, transition(before, "CLOSED"), "CLOSED");

            assertThat(after.instant("closedAt")).isEqualTo(after.instant("updatedAt"));
            assertThat(after.instant("resolvedAt")).isEqualTo(before.instant("resolvedAt"));
            assertThat(after.get("cancelledAt")).isNull();
        }

        @Test
        @DisplayName("TS-SM-A4 OPEN → CANCELLED is allowed and sets cancelledAt")
        void openToCancelled() {
            ApiResponse before = tickets.createInStatus("OPEN");

            ApiResponse after = assertTransitioned(before, transition(before, "CANCELLED"), "CANCELLED");

            assertThat(after.instant("cancelledAt")).isEqualTo(after.instant("updatedAt"));
            assertThat(after.body()).containsEntry("resolvedAt", null).containsEntry("closedAt", null);
        }

        @Test
        @DisplayName("TS-SM-A5 IN_PROGRESS → CANCELLED is allowed and sets cancelledAt")
        void inProgressToCancelled() {
            ApiResponse before = tickets.createInStatus("IN_PROGRESS");

            ApiResponse after = assertTransitioned(before, transition(before, "CANCELLED"), "CANCELLED");

            assertThat(after.instant("cancelledAt")).isEqualTo(after.instant("updatedAt"));
            assertThat(after.body()).containsEntry("resolvedAt", null).containsEntry("closedAt", null);
        }
    }

    @Nested
    @DisplayName("rejected transitions")
    class Rejected {

        @Test
        @DisplayName("TS-SM-R1 CLOSED → OPEN is rejected with 409 and nothing changes")
        void closedToOpen() {
            ApiResponse before = tickets.createInStatus("CLOSED");

            assertRejected(before, transition(before, "OPEN"), "OPEN");
        }

        @Test
        @DisplayName("TS-SM-R2 RESOLVED → OPEN is rejected with 409 and nothing changes")
        void resolvedToOpen() {
            ApiResponse before = tickets.createInStatus("RESOLVED");

            assertRejected(before, transition(before, "OPEN"), "OPEN");
        }

        @Test
        @DisplayName("TS-SM-R3 CANCELLED → OPEN is rejected with 409 and nothing changes")
        void cancelledToOpen() {
            ApiResponse before = tickets.createInStatus("CANCELLED");

            assertRejected(before, transition(before, "OPEN"), "OPEN");
        }

        @Test
        @DisplayName("TS-SM-R4 RESOLVED → IN_PROGRESS (reopen) is rejected (A-20)")
        void resolvedToInProgress() {
            ApiResponse before = tickets.createInStatus("RESOLVED");

            assertRejected(before, transition(before, "IN_PROGRESS"), "IN_PROGRESS");
        }

        @Test
        @DisplayName("TS-SM-R5 OPEN → CLOSED (skipping steps) is rejected")
        void openToClosed() {
            ApiResponse before = tickets.createInStatus("OPEN");

            assertRejected(before, transition(before, "CLOSED"), "CLOSED");
        }

        @Test
        @DisplayName("TS-SM-R6 OPEN → RESOLVED (skipping IN_PROGRESS) is rejected")
        void openToResolved() {
            ApiResponse before = tickets.createInStatus("OPEN");

            assertRejected(before, transition(before, "RESOLVED"), "RESOLVED");
        }

        @Test
        @DisplayName("TS-SM-R7 RESOLVED → CANCELLED is rejected")
        void resolvedToCancelled() {
            ApiResponse before = tickets.createInStatus("RESOLVED");

            assertRejected(before, transition(before, "CANCELLED"), "CANCELLED");
        }

        @Test
        @DisplayName("TS-SM-R8 OPEN → OPEN (self-transition) is rejected (A-21)")
        void openToOpen() {
            ApiResponse before = tickets.createInStatus("OPEN");

            assertRejected(before, transition(before, "OPEN"), "OPEN");
        }
    }

    /** The 25-row matrix of spec/state-machine.md §4, verbatim. */
    static Stream<Arguments> transitionMatrix() {
        return Stream.of(
                Arguments.of(1, "OPEN", "OPEN", false),
                Arguments.of(2, "OPEN", "IN_PROGRESS", true),
                Arguments.of(3, "OPEN", "RESOLVED", false),
                Arguments.of(4, "OPEN", "CLOSED", false),
                Arguments.of(5, "OPEN", "CANCELLED", true),
                Arguments.of(6, "IN_PROGRESS", "OPEN", false),
                Arguments.of(7, "IN_PROGRESS", "IN_PROGRESS", false),
                Arguments.of(8, "IN_PROGRESS", "RESOLVED", true),
                Arguments.of(9, "IN_PROGRESS", "CLOSED", false),
                Arguments.of(10, "IN_PROGRESS", "CANCELLED", true),
                Arguments.of(11, "RESOLVED", "OPEN", false),
                Arguments.of(12, "RESOLVED", "IN_PROGRESS", false),
                Arguments.of(13, "RESOLVED", "RESOLVED", false),
                Arguments.of(14, "RESOLVED", "CLOSED", true),
                Arguments.of(15, "RESOLVED", "CANCELLED", false),
                Arguments.of(16, "CLOSED", "OPEN", false),
                Arguments.of(17, "CLOSED", "IN_PROGRESS", false),
                Arguments.of(18, "CLOSED", "RESOLVED", false),
                Arguments.of(19, "CLOSED", "CLOSED", false),
                Arguments.of(20, "CLOSED", "CANCELLED", false),
                Arguments.of(21, "CANCELLED", "OPEN", false),
                Arguments.of(22, "CANCELLED", "IN_PROGRESS", false),
                Arguments.of(23, "CANCELLED", "RESOLVED", false),
                Arguments.of(24, "CANCELLED", "CLOSED", false),
                Arguments.of(25, "CANCELLED", "CANCELLED", false));
    }

    @ParameterizedTest(name = "#{0} {1} → {2} allowed={3}")
    @MethodSource("transitionMatrix")
    @DisplayName("TS-SM-M3 full 5×5 transition matrix over HTTP")
    void matrix(int row, String current, String target, boolean allowed) {
        ApiResponse before = tickets.createInStatus(current);

        ApiResponse response = transition(before, target);

        if (allowed) {
            assertTransitioned(before, response, target);
        } else {
            assertRejected(before, response, target);
        }
    }

    @Test
    @DisplayName("TS-SM-M3 the matrix has exactly 5 allowed and 20 rejected pairs")
    void matrix_counts() {
        assertThat(transitionMatrix().filter(args -> (boolean) args.get()[3]).count()).isEqualTo(5);
        assertThat(transitionMatrix().count()).isEqualTo(25);
    }

    @Nested
    @DisplayName("full lifecycle paths (TS-SM-M4)")
    class Paths {

        @Test
        @DisplayName("OPEN → IN_PROGRESS → RESOLVED → CLOSED accumulates lifecycle timestamps")
        void happyPath() {
            ApiResponse open = tickets.createInStatus("OPEN");
            ApiResponse inProgress = assertTransitioned(open, transition(open, "IN_PROGRESS"), "IN_PROGRESS");
            ApiResponse resolved = assertTransitioned(inProgress, transition(inProgress, "RESOLVED"), "RESOLVED");
            ApiResponse closed = assertTransitioned(resolved, transition(resolved, "CLOSED"), "CLOSED");

            assertThat(closed.number("version")).isEqualTo(3);
            assertThat(closed.instant("resolvedAt")).isEqualTo(resolved.instant("resolvedAt"));
            assertThat(closed.instant("closedAt")).isAfterOrEqualTo(closed.instant("resolvedAt"));
            assertThat(closed.instant("resolvedAt")).isAfterOrEqualTo(closed.instant("createdAt"));
            assertThat(closed.get("cancelledAt")).isNull();
            assertThat(closed.list("allowedTransitions")).isEmpty();
        }

        @Test
        @DisplayName("OPEN → IN_PROGRESS → CANCELLED")
        void cancelAfterStarting() {
            ApiResponse open = tickets.createInStatus("OPEN");
            ApiResponse inProgress = assertTransitioned(open, transition(open, "IN_PROGRESS"), "IN_PROGRESS");
            ApiResponse cancelled = assertTransitioned(inProgress, transition(inProgress, "CANCELLED"), "CANCELLED");

            assertThat(cancelled.get("resolvedAt")).isNull();
            assertThat(cancelled.get("closedAt")).isNull();
            assertThat(cancelled.instant("cancelledAt")).isNotNull();
        }
    }

    @Nested
    @DisplayName("versioning and precedence")
    class Versioning {

        @Test
        @DisplayName("TS-SM-C3 retrying a committed transition with the old version returns 409 CONCURRENT_MODIFICATION")
        void retryAfterSuccess() {
            ApiResponse open = tickets.createInStatus("OPEN");
            transition(open, "IN_PROGRESS");

            ApiResponse retry = transition(open, "IN_PROGRESS");

            assertProblem(retry, 409, "TICKET_CONCURRENT_MODIFICATION");
            assertThat(tickets.get(open.number("id")).number("version")).isEqualTo(1);
        }

        @Test
        @DisplayName("TS-SM-C5 an edit bumps the version, so a transition with the pre-edit version is rejected")
        void transitionAfterEditWithOldVersion() {
            ApiResponse open = tickets.createInStatus("OPEN");
            api.patch(TicketApi.ticket(open.number("id")), json("version", 0, "title", "Edited"));

            ApiResponse response = transition(open, "IN_PROGRESS");

            assertProblem(response, 409, "TICKET_CONCURRENT_MODIFICATION");
            assertThat(tickets.get(open.number("id")).string("status")).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("TS-SM-C4 stale version + illegal target reports the version conflict first")
        void staleVersionBeatsIllegalTarget() {
            ApiResponse closed = tickets.createInStatus("CLOSED");

            ApiResponse response = tickets.transition(closed.number("id"), 0, "OPEN");

            assertProblem(response, 409, "TICKET_CONCURRENT_MODIFICATION");
        }

        @Test
        @DisplayName("TS-SM-C2 two concurrent identical transitions: exactly one succeeds")
        void concurrentIdenticalTransitions() {
            ApiResponse open = tickets.createInStatus("OPEN");

            CompletableFuture<ApiResponse> first = CompletableFuture.supplyAsync(() -> transition(open, "IN_PROGRESS"));
            CompletableFuture<ApiResponse> second = CompletableFuture.supplyAsync(() -> transition(open, "IN_PROGRESS"));
            List<Integer> statuses = Stream.of(first.join(), second.join()).map(ApiResponse::status).sorted().toList();

            // Outcome contract only. Whether the loser was rejected by the early version check or at commit time
            // needs the deterministic interleaving hook planned for STEP-41 (review SR-09).
            assertThat(statuses).containsExactly(200, 409);
            assertThat(tickets.get(open.number("id")).number("version")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("request validation and enforcement")
    class Enforcement {

        @ParameterizedTest(name = "targetStatus={0}")
        @ValueSource(strings = {"REOPENED", "open"})
        @DisplayName("TS-SM-E4 an unknown targetStatus is 400 INVALID_VALUE, not a 409")
        void unknownTarget_isValidationError(String target) {
            ApiResponse open = tickets.createInStatus("OPEN");

            ApiResponse response = transition(open, target);

            assertValidationErrors(response, FieldError.body("targetStatus", "INVALID_VALUE"));
        }

        @Test
        @DisplayName("TS-SM-E4 missing targetStatus is 400 REQUIRED")
        void missingTarget_isRequired() {
            long id = tickets.createInStatus("OPEN").number("id");

            ApiResponse response = api.post(TicketApi.transitions(id), json("version", 0));

            assertValidationErrors(response, FieldError.body("targetStatus", "REQUIRED"));
        }

        @Test
        @DisplayName("TS-SM-E4 null targetStatus is 400 REQUIRED")
        void nullTarget_isRequired() {
            long id = tickets.createInStatus("OPEN").number("id");

            ApiResponse response = api.post(TicketApi.transitions(id), json("version", 0, "targetStatus", null));

            assertValidationErrors(response, FieldError.body("targetStatus", "REQUIRED"));
        }

        @Test
        @DisplayName("missing version is 400 REQUIRED")
        void missingVersion_isRequired() {
            long id = tickets.createInStatus("OPEN").number("id");

            ApiResponse response = api.post(TicketApi.transitions(id), json("targetStatus", "IN_PROGRESS"));

            assertValidationErrors(response, FieldError.body("version", "REQUIRED"));
            assertThat(tickets.get(id).string("status")).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("TS-SM-E1 status cannot be set on create")
        void create_withStatus_isRejected() {
            ApiResponse response = api.post(TicketApi.TICKETS,
                    json("title", "t", "description", "d", "status", "CLOSED"));

            assertValidationErrors(response, FieldError.body("status", "UNKNOWN_FIELD"));
        }

        @Test
        @DisplayName("transition on an unknown ticket returns 404 TICKET_NOT_FOUND")
        void unknownTicket_returns404() {
            ApiResponse response = tickets.transition(987_654_321_012L, 0, "IN_PROGRESS");

            assertProblem(response, 404, "TICKET_NOT_FOUND");
        }
    }
}
