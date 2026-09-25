package com.supportdesk.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.supportdesk.ticket.application.TicketCommands.AddComment;
import com.supportdesk.ticket.application.TicketViews.CommentView;
import com.supportdesk.ticket.domain.Comment;
import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotCommentableException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotFoundException;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketStatus;
import com.supportdesk.ticket.persistence.CommentRepository;
import com.supportdesk.ticket.persistence.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Comment use cases with mocked repositories: spec/test-strategy.md §4.2 (TS-SVC-02, 08), TC-5, D-5; REQ-5, A-18, A-34. */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketCommentService (Mockito)")
class TicketCommentServiceTest {

    private static final long TICKET_ID = 7;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TicketRepository tickets;
    @Mock
    private CommentRepository comments;

    private TicketCommentService service;

    @BeforeEach
    void setUp() {
        service = new TicketCommentService(tickets, comments, CLOCK);
    }

    private static Ticket ticketIn(TicketStatus status) {
        Ticket ticket = Ticket.create("Printer jam", "Tray 2", TicketPriority.MEDIUM, null, CLOCK);
        ReflectionTestUtils.setField(ticket, "id", TICKET_ID);
        switch (status) {
            case OPEN -> { }
            case IN_PROGRESS -> ticket.changeStatus(TicketStatus.IN_PROGRESS, CLOCK);
            case RESOLVED -> {
                ticket.changeStatus(TicketStatus.IN_PROGRESS, CLOCK);
                ticket.changeStatus(TicketStatus.RESOLVED, CLOCK);
            }
            case CLOSED -> {
                ticket.changeStatus(TicketStatus.IN_PROGRESS, CLOCK);
                ticket.changeStatus(TicketStatus.RESOLVED, CLOCK);
                ticket.changeStatus(TicketStatus.CLOSED, CLOCK);
            }
            case CANCELLED -> ticket.changeStatus(TicketStatus.CANCELLED, CLOCK);
        }
        return ticket;
    }

    @ParameterizedTest(name = "TS-SVC-08 comment on a {0} ticket is saved with the clock time; the ticket is untouched")
    @EnumSource(value = TicketStatus.class, names = {"OPEN", "IN_PROGRESS", "RESOLVED"})
    void commentIsSavedForNonTerminalTicketsWithoutTouchingTheTicket(TicketStatus status) {
        Ticket ticket = ticketIn(status);
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticket));
        when(comments.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        Instant updatedAt = ticket.getUpdatedAt();

        CommentView view = service.add(new AddComment(TICKET_ID, "maria", "On it"));

        assertThat(view.ticketId()).isEqualTo(TICKET_ID);
        assertThat(view.author()).isEqualTo("maria");
        assertThat(view.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(ticket.getUpdatedAt()).as("A-34: comments don't change updatedAt").isEqualTo(updatedAt);
        verify(tickets, never()).flush();
        verify(tickets, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"CLOSED", "CANCELLED"})
    void terminalTicketsRejectCommentsAndNothingIsSaved(TicketStatus status) {
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.of(ticketIn(status)));

        assertThatThrownBy(() -> service.add(new AddComment(TICKET_ID, "maria", "Late")))
                .isInstanceOf(TicketNotCommentableException.class);
        verifyNoInteractions(comments);
    }

    @Test
    @DisplayName("TS-SVC-02 adding to an unknown ticket is not found and saves nothing")
    void addingToAnUnknownTicketIsNotFound() {
        when(tickets.findById(TICKET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(new AddComment(TICKET_ID, "maria", "Hello")))
                .isInstanceOf(TicketNotFoundException.class);
        verifyNoInteractions(comments);
    }

    @Test
    void listingCommentsOfAnUnknownTicketIsNotFoundWithoutQueryingComments() {
        when(tickets.existsById(TICKET_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.list(TICKET_ID, 0, 50)).isInstanceOf(TicketNotFoundException.class);
        verifyNoInteractions(comments);
    }
}
