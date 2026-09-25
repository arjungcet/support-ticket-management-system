package com.supportdesk.ticket.api;

import com.supportdesk.ticket.application.TicketViews.CommentView;
import com.supportdesk.ticket.application.TicketViews.PageView;
import com.supportdesk.ticket.application.TicketViews.TicketSummaryView;
import com.supportdesk.ticket.application.TicketViews.TicketView;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/** Response representations of spec/api-contract.md §4. Nullable properties are always serialised (as null). */
final class TicketResponses {

    private TicketResponses() {
    }

    record TicketResponse(long id, String title, String description, TicketPriority priority, TicketStatus status,
            String assignee, Instant createdAt, Instant updatedAt, Instant resolvedAt, Instant closedAt,
            Instant cancelledAt, long version, List<TicketStatus> allowedTransitions) {

        static TicketResponse of(TicketView view) {
            return new TicketResponse(view.id(), view.title(), view.description(), view.priority(), view.status(),
                    view.assignee(), view.createdAt(), view.updatedAt(), view.resolvedAt(), view.closedAt(),
                    view.cancelledAt(), view.version(), view.allowedTransitions());
        }
    }

    record TicketSummaryResponse(long id, String title, TicketPriority priority, TicketStatus status, String assignee,
            Instant createdAt, Instant updatedAt) {

        static TicketSummaryResponse of(TicketSummaryView view) {
            return new TicketSummaryResponse(view.id(), view.title(), view.priority(), view.status(), view.assignee(),
                    view.createdAt(), view.updatedAt());
        }
    }

    record CommentResponse(long id, long ticketId, String author, String body, Instant createdAt) {

        static CommentResponse of(CommentView view) {
            return new CommentResponse(view.id(), view.ticketId(), view.author(), view.body(), view.createdAt());
        }
    }

    record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }

    record PageResponse<T>(List<T> content, PageMetadata page) {

        static <V, T> PageResponse<T> of(PageView<V> view, Function<V, T> mapper) {
            return new PageResponse<>(view.content().stream().map(mapper).toList(),
                    new PageMetadata(view.number(), view.size(), view.totalElements(), view.totalPages()));
        }
    }
}
