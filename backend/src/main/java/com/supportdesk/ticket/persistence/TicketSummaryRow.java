package com.supportdesk.ticket.persistence;

import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketStatus;
import java.time.Instant;

/** List-row projection: no description, no comments (spec/data-model.md §4.4, TS-REPO-07). */
public record TicketSummaryRow(Long id, String title, TicketPriority priority, TicketStatus status, String assignee,
        Instant createdAt, Instant updatedAt) {
}
