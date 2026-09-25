package com.supportdesk.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supportdesk.ticket.application.TicketCommands.AssignTicket;
import com.supportdesk.ticket.application.TicketCommands.ChangeStatus;
import com.supportdesk.ticket.application.TicketCommands.CreateTicket;
import com.supportdesk.ticket.application.TicketCommands.UpdateTicket;
import com.supportdesk.ticket.application.TicketViews.PageView;
import com.supportdesk.ticket.application.TicketViews.TicketSummaryView;
import com.supportdesk.ticket.application.TicketViews.TicketView;
import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketExceptions.ConcurrentModificationException;
import com.supportdesk.ticket.domain.TicketExceptions.InvalidStatusTransitionException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotEditableException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotFoundException;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import com.supportdesk.ticket.domain.TicketStatus;
import com.supportdesk.ticket.persistence.TicketRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit tests of the ticket use cases with a mocked repository: spec/test-strategy.md §4.2 (TS-SVC-01…07, 09, 10),
 * TC-5, decision D-5. They pin the orchestration rules of spec/state-machine.md §6: the version is checked before
 * anything else, rejected commands never flush, and a command that changes nothing doesn't write. Persistence itself
 * is covered on PostgreSQL by the integration tests.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TicketService (Mockito)")
class TicketServiceTest {

    private static final long ID = 42;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-26T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TicketRepository tickets;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(tickets, CLOCK);
    }

    /** A persisted-looking ticket: the repository would assign the id, so the test sets it. */
    private static Ticket storedTicket() {
        Ticket ticket = Ticket.create("Printer jam", "Tray 2", TicketPriority.MEDIUM, null, CLOCK);
        ReflectionTestUtils.setField(ticket, "id", ID);
        return ticket;
    }

    private Ticket givenStored(Ticket ticket) {
        when(tickets.findById(ID)).thenReturn(Optional.of(ticket));
        return ticket;
    }

    @Test
    @DisplayName("TS-SVC-01 create saves an OPEN ticket and returns its view")
    void createSavesAnOpenTicketAndReturnsIt() {
        when(tickets.saveAndFlush(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", ID);
            return saved;
        });

        TicketView view = service.create(new CreateTicket("Printer jam", "Tray 2", TicketPriority.HIGH, "maria"));

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(view.createdAt()).isEqualTo(CLOCK.instant());
        assertThat(view.allowedTransitions()).containsExactly(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("TS-SVC-02 every command on an unknown id is not found and writes nothing")
    void unknownTicketIsNotFoundForEveryCommand() {
        when(tickets.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ID)).isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> service.update(new UpdateTicket(ID, 0, "t", null, null)))
                .isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> service.assign(new AssignTicket(ID, 0, "maria")))
                .isInstanceOf(TicketNotFoundException.class);
        assertThatThrownBy(() -> service.changeStatus(new ChangeStatus(ID, 0, TicketStatus.IN_PROGRESS)))
                .isInstanceOf(TicketNotFoundException.class);
        verify(tickets, never()).flush();
    }

    @Test
    @DisplayName("TS-SVC-09 search passes the criteria and paging to the repository unchanged")
    void searchDelegatesCriteriaAndPaging() {
        TicketSearchCriteria criteria = new TicketSearchCriteria("printer", Set.of(TicketStatus.OPEN),
                TicketSearchCriteria.SortField.PRIORITY, false);
        when(tickets.searchSummaries(criteria, PageRequest.of(2, 10))).thenReturn(Page.empty(PageRequest.of(2, 10)));

        PageView<TicketSummaryView> page = service.search(criteria, 2, 10);

        verify(tickets).searchSummaries(criteria, PageRequest.of(2, 10));
        assertThat(page.content()).isEmpty();
        assertThat(page.number()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("TS-SVC-10 mutating use cases are transactional, queries are read-only transactions")
    void transactionBoundaries() throws NoSuchMethodException {
        for (String name : List.of("create", "update", "assign", "changeStatus")) {
            Method method = Arrays.stream(TicketService.class.getMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            assertThat(method.getAnnotation(Transactional.class)).as(name).isNotNull()
                    .extracting(Transactional::readOnly).isEqualTo(false);
        }
        for (Method method : List.of(TicketService.class.getMethod("get", long.class),
                TicketService.class.getMethod("search", TicketSearchCriteria.class, int.class, int.class),
                TicketCommentService.class.getMethod("list", long.class, int.class, int.class))) {
            assertThat(method.getAnnotation(Transactional.class)).as(method.getName()).isNotNull()
                    .extracting(Transactional::readOnly).isEqualTo(true);
        }
        assertThat(TicketCommentService.class.getMethod("add", TicketCommands.AddComment.class)
                .getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Nested
    @DisplayName("modifying commands")
    class Modifying {

        @Test
        @DisplayName("TS-SVC-03 a stale version is rejected before any change, for update, assign and transition")
        void staleVersionIsRejectedBeforeAnyChangeAndNothingIsFlushed() {
            Ticket ticket = givenStored(storedTicket());

            assertThatThrownBy(() -> service.update(new UpdateTicket(ID, 7, "New title", null, null)))
                    .isInstanceOf(ConcurrentModificationException.class);
            assertThatThrownBy(() -> service.assign(new AssignTicket(ID, 7, "maria")))
                    .isInstanceOf(ConcurrentModificationException.class);
            assertThatThrownBy(() -> service.changeStatus(new ChangeStatus(ID, 7, TicketStatus.IN_PROGRESS)))
                    .isInstanceOf(ConcurrentModificationException.class);

            assertThat(ticket.getTitle()).isEqualTo("Printer jam");
            assertThat(ticket.getAssignee()).isNull();
            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
            verify(tickets, never()).flush();
        }

        @Test
        @DisplayName("TS-SVC-04 stale version and illegal transition: the version conflict wins")
        void versionIsCheckedBeforeTransitionLegality() {
            givenStored(storedTicket());

            // OPEN -> CLOSED is illegal, but the stale version is reported first (state-machine.md §6.1).
            assertThatThrownBy(() -> service.changeStatus(new ChangeStatus(ID, 3, TicketStatus.CLOSED)))
                    .isInstanceOf(ConcurrentModificationException.class);
            verify(tickets, never()).flush();
        }

        @Test
        void updateWithChangesFlushesSoTheResponseCarriesTheNewVersion() {
            givenStored(storedTicket());

            TicketView view = service.update(new UpdateTicket(ID, 0, "Printer jam on floor 3", null, null));

            verify(tickets).flush();
            assertThat(view.title()).isEqualTo("Printer jam on floor 3");
        }

        @Test
        @DisplayName("TS-SVC-07 an update to identical values doesn't write and keeps version and updatedAt")
        void updateToIdenticalValuesDoesNotWrite() {
            Ticket ticket = givenStored(storedTicket());
            Instant updatedAt = ticket.getUpdatedAt();

            TicketView view = service.update(new UpdateTicket(ID, 0, "Printer jam", "Tray 2", TicketPriority.MEDIUM));

            verify(tickets, never()).flush();
            assertThat(view.version()).isZero();
            assertThat(view.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        void assigningTheSameAssigneeDoesNotWrite() {
            givenStored(storedTicket());

            service.assign(new AssignTicket(ID, 0, null));

            verify(tickets, never()).flush();
        }

        @Test
        @DisplayName("TS-SVC-05 a legal transition is applied and flushed")
        void legalTransitionIsAppliedAndFlushed() {
            givenStored(storedTicket());

            TicketView view = service.changeStatus(new ChangeStatus(ID, 0, TicketStatus.IN_PROGRESS));

            verify(tickets).flush();
            assertThat(view.status()).isEqualTo(TicketStatus.IN_PROGRESS);
            assertThat(view.allowedTransitions()).containsExactly(TicketStatus.RESOLVED, TicketStatus.CANCELLED);
        }

        @Test
        @DisplayName("TS-SVC-06 an illegal transition propagates the domain exception and writes nothing")
        void illegalTransitionIsRejectedWithoutWriting() {
            Ticket ticket = givenStored(storedTicket());

            assertThatThrownBy(() -> service.changeStatus(new ChangeStatus(ID, 0, TicketStatus.RESOLVED)))
                    .isInstanceOf(InvalidStatusTransitionException.class);

            assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
            verify(tickets, never()).flush();
        }

        @Test
        void terminalTicketCannotBeEditedOrAssigned() {
            Ticket ticket = storedTicket();
            ticket.changeStatus(TicketStatus.CANCELLED, CLOCK);
            givenStored(ticket);

            assertThatThrownBy(() -> service.update(new UpdateTicket(ID, 0, "x", null, null)))
                    .isInstanceOf(TicketNotEditableException.class);
            assertThatThrownBy(() -> service.assign(new AssignTicket(ID, 0, "maria")))
                    .isInstanceOf(TicketNotEditableException.class);
            verify(tickets, never()).flush();
        }

    }
}
