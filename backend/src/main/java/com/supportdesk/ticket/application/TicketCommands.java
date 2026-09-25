package com.supportdesk.ticket.application;

import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketStatus;

/** Use-case inputs, independent of HTTP (spec/architecture.md §10). Text values are already validated and trimmed. */
public final class TicketCommands {

    private TicketCommands() {
    }

    public record CreateTicket(String title, String description, TicketPriority priority, String assignee) {
    }

    /** {@code null} fields are unchanged. */
    public record UpdateTicket(long ticketId, long version, String title, String description,
            TicketPriority priority) {
    }

    /** {@code assignee == null} unassigns. */
    public record AssignTicket(long ticketId, long version, String assignee) {
    }

    public record ChangeStatus(long ticketId, long version, TicketStatus targetStatus) {
    }

    public record AddComment(long ticketId, String author, String body) {
    }
}
