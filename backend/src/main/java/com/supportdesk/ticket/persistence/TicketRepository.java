package com.supportdesk.ticket.persistence;

import com.supportdesk.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long>, TicketSearchQueries {
}
