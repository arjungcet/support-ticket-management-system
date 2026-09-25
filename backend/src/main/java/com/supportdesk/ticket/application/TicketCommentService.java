package com.supportdesk.ticket.application;

import com.supportdesk.ticket.application.TicketCommands.AddComment;
import com.supportdesk.ticket.application.TicketViews.CommentView;
import com.supportdesk.ticket.application.TicketViews.PageView;
import com.supportdesk.ticket.domain.Comment;
import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotFoundException;
import com.supportdesk.ticket.persistence.CommentRepository;
import com.supportdesk.ticket.persistence.TicketRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comment use cases (REQ-5). Adding a comment never changes the ticket's version or updatedAt (A-34, DM-6). */
@Service
public class TicketCommentService {

    private static final Logger log = LoggerFactory.getLogger(TicketCommentService.class);

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final Clock clock;

    public TicketCommentService(TicketRepository tickets, CommentRepository comments, Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.clock = clock;
    }

    @Transactional
    public CommentView add(AddComment command) {
        Ticket ticket = tickets.findById(command.ticketId())
                .orElseThrow(() -> new TicketNotFoundException(command.ticketId()));
        ticket.ensureCommentable();
        Comment comment = comments.saveAndFlush(Comment.create(ticket.getId(), command.author(), command.body(), clock));
        log.info("Comment {} added to ticket {}", comment.getId(), ticket.getId());
        return CommentView.of(comment);
    }

    @Transactional(readOnly = true)
    public PageView<CommentView> list(long ticketId, int page, int size) {
        if (!tickets.existsById(ticketId)) {
            throw new TicketNotFoundException(ticketId);
        }
        return PageView.of(comments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId, PageRequest.of(page, size)),
                CommentView::of);
    }
}
