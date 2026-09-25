package com.supportdesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** TS-UNIT-01 / TS-SM-M1: the transition table against the 25-row oracle of spec/state-machine.md §4. */
class TicketStatusTest {

    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of("OPEN", "OPEN", false), Arguments.of("OPEN", "IN_PROGRESS", true),
                Arguments.of("OPEN", "RESOLVED", false), Arguments.of("OPEN", "CLOSED", false),
                Arguments.of("OPEN", "CANCELLED", true),
                Arguments.of("IN_PROGRESS", "OPEN", false), Arguments.of("IN_PROGRESS", "IN_PROGRESS", false),
                Arguments.of("IN_PROGRESS", "RESOLVED", true), Arguments.of("IN_PROGRESS", "CLOSED", false),
                Arguments.of("IN_PROGRESS", "CANCELLED", true),
                Arguments.of("RESOLVED", "OPEN", false), Arguments.of("RESOLVED", "IN_PROGRESS", false),
                Arguments.of("RESOLVED", "RESOLVED", false), Arguments.of("RESOLVED", "CLOSED", true),
                Arguments.of("RESOLVED", "CANCELLED", false),
                Arguments.of("CLOSED", "OPEN", false), Arguments.of("CLOSED", "IN_PROGRESS", false),
                Arguments.of("CLOSED", "RESOLVED", false), Arguments.of("CLOSED", "CLOSED", false),
                Arguments.of("CLOSED", "CANCELLED", false),
                Arguments.of("CANCELLED", "OPEN", false), Arguments.of("CANCELLED", "IN_PROGRESS", false),
                Arguments.of("CANCELLED", "RESOLVED", false), Arguments.of("CANCELLED", "CLOSED", false),
                Arguments.of("CANCELLED", "CANCELLED", false));
    }

    @ParameterizedTest(name = "{0} → {1} allowed={2}")
    @MethodSource("matrix")
    @DisplayName("every (from, to) pair matches the specified matrix")
    void canTransitionTo_matchesMatrix(String from, String to, boolean allowed) {
        assertThat(TicketStatus.valueOf(from).canTransitionTo(TicketStatus.valueOf(to))).isEqualTo(allowed);
    }

    @Test
    @DisplayName("exactly 5 of the 25 pairs are allowed, and the matrix covers every status")
    void matrix_isComplete() {
        long allowed = Arrays.stream(TicketStatus.values())
                .flatMap(from -> Arrays.stream(TicketStatus.values()).filter(from::canTransitionTo))
                .count();

        assertThat(allowed).isEqualTo(5);
        assertThat(matrix().count()).isEqualTo((long) TicketStatus.values().length * TicketStatus.values().length);
    }

    @Test
    @DisplayName("allowed targets are returned in contract order; terminal states have none")
    void allowedTargets_andTerminal() {
        assertThat(TicketStatus.OPEN.allowedTargets()).containsExactly(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED);
        assertThat(TicketStatus.IN_PROGRESS.allowedTargets())
                .containsExactly(TicketStatus.RESOLVED, TicketStatus.CANCELLED);
        assertThat(TicketStatus.RESOLVED.allowedTargets()).containsExactly(TicketStatus.CLOSED);
        assertThat(Arrays.stream(TicketStatus.values()).filter(TicketStatus::isTerminal))
                .containsExactlyInAnyOrder(TicketStatus.CLOSED, TicketStatus.CANCELLED);
    }
}
