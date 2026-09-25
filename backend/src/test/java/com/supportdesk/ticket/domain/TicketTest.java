package com.supportdesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.supportdesk.shared.error.ErrorCode;
import com.supportdesk.ticket.domain.TicketExceptions.InvalidStatusTransitionException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotCommentableException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotEditableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Ticket aggregate rules (spec/state-machine.md §3, §8; TS-UNIT-02…06, TS-SM-A1…A5, R1…R8, M2). */
class TicketTest {

    private static final Instant T0 = Instant.parse("2026-09-26T08:00:00Z");
    private static final Clock AT_T0 = Clock.fixed(T0, ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(T0.plus(Duration.ofMinutes(5)), ZoneOffset.UTC);

    /** Legal path from OPEN to each status (fixtures never create impossible states). */
    private static final Map<TicketStatus, List<TicketStatus>> PATH = Map.of(
            TicketStatus.OPEN, List.of(),
            TicketStatus.IN_PROGRESS, List.of(TicketStatus.IN_PROGRESS),
            TicketStatus.RESOLVED, List.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
            TicketStatus.CLOSED, List.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED),
            TicketStatus.CANCELLED, List.of(TicketStatus.CANCELLED));

    private static Ticket ticketIn(TicketStatus status) {
        Ticket ticket = Ticket.create("Printer jam", "Paper stuck", TicketPriority.HIGH, "sam", AT_T0);
        PATH.get(status).forEach(next -> ticket.changeStatus(next, AT_T0));
        return ticket;
    }

    private record Snapshot(TicketStatus status, Instant updatedAt, Instant resolvedAt, Instant closedAt,
            Instant cancelledAt, String title, String assignee) {

        static Snapshot of(Ticket t) {
            return new Snapshot(t.getStatus(), t.getUpdatedAt(), t.getResolvedAt(), t.getClosedAt(),
                    t.getCancelledAt(), t.getTitle(), t.getAssignee());
        }
    }

    @Test
    @DisplayName("TS-UNIT-02 a new ticket is OPEN with createdAt == updatedAt and no lifecycle timestamps")
    void create_isOpen() {
        Ticket ticket = Ticket.create("Printer jam", "Paper stuck", TicketPriority.LOW, null, AT_T0);

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getCreatedAt()).isEqualTo(T0).isEqualTo(ticket.getUpdatedAt());
        assertThat(ticket.getResolvedAt()).isNull();
        assertThat(ticket.getClosedAt()).isNull();
        assertThat(ticket.getCancelledAt()).isNull();
    }

    @Test
    @DisplayName("TS-UNIT-06 blank title or description is rejected even when called directly")
    void create_rejectsBlankText() {
        assertThatThrownBy(() -> Ticket.create(" ", "d", TicketPriority.LOW, null, AT_T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Ticket.create("t", "", TicketPriority.LOW, null, AT_T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Nested
    @DisplayName("allowed transitions")
    class Allowed {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"OPEN,IN_PROGRESS", "IN_PROGRESS,RESOLVED", "RESOLVED,CLOSED", "OPEN,CANCELLED",
                "IN_PROGRESS,CANCELLED"})
        @DisplayName("TS-SM-A1…A5 change the status and updatedAt")
        void allowed(TicketStatus from, TicketStatus to) {
            Ticket ticket = ticketIn(from);

            ticket.changeStatus(to, LATER);

            assertThat(ticket.getStatus()).isEqualTo(to);
            assertThat(ticket.getUpdatedAt()).isEqualTo(LATER.instant());
        }

        @Test
        @DisplayName("TS-SM-A2 RESOLVED sets resolvedAt; A3 CLOSED sets closedAt and keeps resolvedAt")
        void lifecycleTimestamps() {
            Ticket ticket = ticketIn(TicketStatus.IN_PROGRESS);

            ticket.changeStatus(TicketStatus.RESOLVED, AT_T0);
            ticket.changeStatus(TicketStatus.CLOSED, LATER);

            assertThat(ticket.getResolvedAt()).isEqualTo(T0);
            assertThat(ticket.getClosedAt()).isEqualTo(LATER.instant());
            assertThat(ticket.getCancelledAt()).isNull();
        }

        @ParameterizedTest(name = "{0} → CANCELLED")
        @EnumSource(value = TicketStatus.class, names = {"OPEN", "IN_PROGRESS"})
        @DisplayName("TS-SM-A4/A5 CANCELLED sets cancelledAt only")
        void cancel(TicketStatus from) {
            Ticket ticket = ticketIn(from);

            ticket.changeStatus(TicketStatus.CANCELLED, LATER);

            assertThat(ticket.getCancelledAt()).isEqualTo(LATER.instant());
            assertThat(ticket.getResolvedAt()).isNull();
            assertThat(ticket.getClosedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("rejected transitions")
    class Rejected {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "CLOSED,OPEN", "RESOLVED,OPEN", "CANCELLED,OPEN", // TS-SM-R1, R2, R3 (REQ-12 examples)
                "RESOLVED,IN_PROGRESS", "OPEN,CLOSED", "OPEN,RESOLVED", "RESOLVED,CANCELLED", "OPEN,OPEN",
                "IN_PROGRESS,OPEN", "IN_PROGRESS,IN_PROGRESS", "IN_PROGRESS,CLOSED", "RESOLVED,RESOLVED",
                "CLOSED,IN_PROGRESS", "CLOSED,RESOLVED", "CLOSED,CLOSED", "CLOSED,CANCELLED",
                "CANCELLED,IN_PROGRESS", "CANCELLED,RESOLVED", "CANCELLED,CLOSED", "CANCELLED,CANCELLED"})
        @DisplayName("TS-SM-R1…R8 / M2: all 20 rejected pairs throw and leave the ticket unchanged")
        void rejected(TicketStatus from, TicketStatus to) {
            Ticket ticket = ticketIn(from);
            Snapshot before = Snapshot.of(ticket);

            assertThatThrownBy(() -> ticket.changeStatus(to, LATER))
                    .isInstanceOfSatisfying(InvalidStatusTransitionException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.TICKET_INVALID_TRANSITION);
                        assertThat(e.currentStatus()).isEqualTo(from);
                        assertThat(e.targetStatus()).isEqualTo(to);
                        assertThat(e.extensions()).containsEntry("allowedTransitions", from.allowedTargets());
                    });
            assertThat(Snapshot.of(ticket)).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("editing, assigning and commenting (A-17, A-18)")
    class Edits {

        @Test
        @DisplayName("TS-UNIT-03 updates only the given fields and bumps updatedAt")
        void updateDetails_partial() {
            Ticket ticket = ticketIn(TicketStatus.OPEN);

            boolean changed = ticket.updateDetails("New title", null, null, LATER);

            assertThat(changed).isTrue();
            assertThat(ticket.getTitle()).isEqualTo("New title");
            assertThat(ticket.getDescription()).isEqualTo("Paper stuck");
            assertThat(ticket.getPriority()).isEqualTo(TicketPriority.HIGH);
            assertThat(ticket.getUpdatedAt()).isEqualTo(LATER.instant());
        }

        @Test
        @DisplayName("TS-UNIT-03 identical values are a no-op: updatedAt unchanged")
        void updateDetails_noOp() {
            Ticket ticket = ticketIn(TicketStatus.OPEN);

            boolean changed = ticket.updateDetails("Printer jam", "Paper stuck", TicketPriority.HIGH, LATER);

            assertThat(changed).isFalse();
            assertThat(ticket.getUpdatedAt()).isEqualTo(T0);
        }

        @Test
        @DisplayName("updatedAt never moves backwards (clock skew, SR-16)")
        void updatedAt_neverBackwards() {
            Ticket ticket = Ticket.create("t", "d", TicketPriority.LOW, null, LATER);

            ticket.assign("sam", AT_T0);

            assertThat(ticket.getUpdatedAt()).isEqualTo(LATER.instant());
        }

        @Test
        @DisplayName("assign and unassign; the same assignee is a no-op; status is untouched (A-19)")
        void assign() {
            Ticket ticket = ticketIn(TicketStatus.OPEN);

            assertThat(ticket.assign("maria", LATER)).isTrue();
            assertThat(ticket.assign("maria", LATER)).isFalse();
            assertThat(ticket.assign(null, LATER)).isTrue();
            assertThat(ticket.getAssignee()).isNull();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TicketStatus.class, names = {"CLOSED", "CANCELLED"})
        @DisplayName("TS-UNIT-04/05 terminal tickets can't be edited, assigned or commented on, and stay unchanged")
        void terminal_isFrozen(TicketStatus status) {
            Ticket ticket = ticketIn(status);
            Snapshot before = Snapshot.of(ticket);

            assertThatThrownBy(() -> ticket.updateDetails("x", null, null, LATER))
                    .isInstanceOf(TicketNotEditableException.class);
            assertThatThrownBy(() -> ticket.assign("x", LATER)).isInstanceOf(TicketNotEditableException.class);
            assertThatThrownBy(ticket::ensureCommentable).isInstanceOf(TicketNotCommentableException.class);
            assertThat(Snapshot.of(ticket)).isEqualTo(before);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = TicketStatus.class, names = {"OPEN", "IN_PROGRESS", "RESOLVED"})
        @DisplayName("non-terminal tickets can be edited and commented on")
        void nonTerminal_isEditable(TicketStatus status) {
            Ticket ticket = ticketIn(status);

            assertThat(ticket.updateDetails("Changed", null, null, LATER)).isTrue();
            ticket.ensureCommentable();
        }
    }
}
