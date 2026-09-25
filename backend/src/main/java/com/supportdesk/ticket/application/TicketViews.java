package com.supportdesk.ticket.application;

import com.supportdesk.ticket.domain.Comment;
import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketStatus;
import com.supportdesk.ticket.persistence.TicketSummaryRow;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Use-case outputs, built inside the transaction so entities never leave the application layer. */
public final class TicketViews {

    private TicketViews() {
    }

    public record TicketView(long id, String title, String description, TicketPriority priority, TicketStatus status,
            String assignee, Instant createdAt, Instant updatedAt, Instant resolvedAt, Instant closedAt,
            Instant cancelledAt, long version, List<TicketStatus> allowedTransitions) {

        static TicketView of(Ticket ticket) {
            return new TicketView(ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getPriority(),
                    ticket.getStatus(), ticket.getAssignee(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                    ticket.getResolvedAt(), ticket.getClosedAt(), ticket.getCancelledAt(), ticket.getVersion(),
                    ticket.getStatus().allowedTargets());
        }
    }

    public record TicketSummaryView(long id, String title, TicketPriority priority, TicketStatus status,
            String assignee, Instant createdAt, Instant updatedAt) {

        static TicketSummaryView of(TicketSummaryRow row) {
            return new TicketSummaryView(row.id(), row.title(), row.priority(), row.status(), row.assignee(),
                    row.createdAt(), row.updatedAt());
        }
    }

    public record CommentView(long id, long ticketId, String author, String body, Instant createdAt) {

        static CommentView of(Comment comment) {
            return new CommentView(comment.getId(), comment.getTicketId(), comment.getAuthor(), comment.getBody(),
                    comment.getCreatedAt());
        }
    }

    public record PageView<T>(List<T> content, int number, int size, long totalElements, int totalPages) {

        static <E, T> PageView<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageView<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
