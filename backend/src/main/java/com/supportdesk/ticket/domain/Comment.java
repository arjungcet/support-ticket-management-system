package com.supportdesk.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

/**
 * A comment on a ticket (REQ-5). Append-only (DM-1): no mutators. Linked by {@code ticketId}, not mapped as a
 * collection on {@link Ticket} (spec/architecture.md §7, A-16).
 */
@Entity
@Table(name = "ticket_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(nullable = false, length = FieldLimits.COMMENT_AUTHOR)
    private String author;

    @Column(nullable = false, length = FieldLimits.COMMENT_BODY)
    private String body;

    @Column(nullable = false)
    private Instant createdAt;

    protected Comment() {
        // for JPA
    }

    public static Comment create(long ticketId, String author, String body, Clock clock) {
        if (author == null || author.isBlank() || body == null || body.isBlank()) {
            throw new IllegalArgumentException("author and body must not be blank");
        }
        Comment comment = new Comment();
        comment.ticketId = ticketId;
        comment.author = author;
        comment.body = body;
        comment.createdAt = clock.instant();
        return comment;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
