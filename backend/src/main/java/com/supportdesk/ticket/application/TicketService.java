package com.supportdesk.ticket.application;

import com.supportdesk.ticket.application.TicketCommands.AssignTicket;
import com.supportdesk.ticket.application.TicketCommands.ChangeStatus;
import com.supportdesk.ticket.application.TicketCommands.CreateTicket;
import com.supportdesk.ticket.application.TicketCommands.UpdateTicket;
import com.supportdesk.ticket.application.TicketViews.PageView;
import com.supportdesk.ticket.application.TicketViews.TicketSummaryView;
import com.supportdesk.ticket.application.TicketViews.TicketView;
import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketExceptions.ConcurrentModificationException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotFoundException;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import com.supportdesk.ticket.domain.TicketStatus;
import com.supportdesk.ticket.persistence.TicketRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ticket use cases. One use case = one transaction (spec/architecture.md §16). Business rules live in {@link Ticket};
 * this service loads, checks the client's version first (spec/state-machine.md §6.1), delegates, and flushes so the
 * returned view carries the new version. A conflict that surfaces at flush time becomes the same 409.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository tickets;
    private final Clock clock;

    public TicketService(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    @Transactional
    public TicketView create(CreateTicket command) {
        Ticket ticket = tickets.saveAndFlush(
                Ticket.create(command.title(), command.description(), command.priority(), command.assignee(), clock));
        log.info("Ticket {} created", ticket.getId());
        return TicketView.of(ticket);
    }

    @Transactional(readOnly = true)
    public TicketView get(long ticketId) {
        return TicketView.of(load(ticketId));
    }

    @Transactional(readOnly = true)
    public PageView<TicketSummaryView> search(TicketSearchCriteria criteria, int page, int size) {
        return PageView.of(tickets.searchSummaries(criteria, PageRequest.of(page, size)), TicketSummaryView::of);
    }

    @Transactional
    public TicketView update(UpdateTicket command) {
        Ticket ticket = loadAtVersion(command.ticketId(), command.version());
        if (ticket.updateDetails(command.title(), command.description(), command.priority(), clock)) {
            tickets.flush();
            log.info("Ticket {} details updated", ticket.getId());
        }
        return TicketView.of(ticket);
    }

    @Transactional
    public TicketView assign(AssignTicket command) {
        Ticket ticket = loadAtVersion(command.ticketId(), command.version());
        if (ticket.assign(command.assignee(), clock)) {
            tickets.flush();
            log.info("Ticket {} assignee changed", ticket.getId());
        }
        return TicketView.of(ticket);
    }

    @Transactional
    public TicketView changeStatus(ChangeStatus command) {
        Ticket ticket = loadAtVersion(command.ticketId(), command.version());
        TicketStatus from = ticket.getStatus();
        ticket.changeStatus(command.targetStatus(), clock);
        tickets.flush();
        log.info("Ticket {} status changed {} -> {}", ticket.getId(), from, ticket.getStatus());
        return TicketView.of(ticket);
    }

    Ticket load(long ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private Ticket loadAtVersion(long ticketId, long version) {
        Ticket ticket = load(ticketId);
        if (ticket.getVersion() != version) {
            throw new ConcurrentModificationException(ticket.getId(), ticket.getVersion());
        }
        return ticket;
    }
}
