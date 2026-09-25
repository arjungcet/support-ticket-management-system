package com.supportdesk.ticket.domain;

import com.supportdesk.ticket.domain.TicketExceptions.InvalidStatusTransitionException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotCommentableException;
import com.supportdesk.ticket.domain.TicketExceptions.TicketNotEditableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Ticket aggregate root (spec/architecture.md §7). State changes only through its methods — there are no setters,
 * and {@link #changeStatus} is the only way to change {@code status} (spec/state-machine.md §5, E3). Every rule is
 * checked before any field is modified, so a rejected call leaves the ticket unchanged.
 */
@Entity
@Table(name = "ticket")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = FieldLimits.TITLE)
    private String title;

    @Column(nullable = false, length = FieldLimits.DESCRIPTION)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketPriority priority;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(length = FieldLimits.ASSIGNEE)
    private String assignee;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant resolvedAt;

    private Instant closedAt;

    private Instant cancelledAt;

    @Version
    private long version;

    protected Ticket() {
        // for JPA
    }

    /** A new ticket is always OPEN (REQ-1, state-machine.md §1). Inputs are expected to be validated and trimmed. */
    public static Ticket create(String title, String description, TicketPriority priority, String assignee,
            Clock clock) {
        Ticket ticket = new Ticket();
        ticket.title = requireText(title, "title");
        ticket.description = requireText(description, "description");
        ticket.priority = Objects.requireNonNull(priority, "priority");
        ticket.assignee = assignee;
        ticket.status = TicketStatus.OPEN;
        Instant now = clock.instant();
        ticket.createdAt = now;
        ticket.updatedAt = now;
        return ticket;
    }

    /**
     * REQ-4: updates the given fields ({@code null} = unchanged). Terminal tickets can't be edited (A-17).
     *
     * @return whether anything changed; an update to identical values changes nothing, not even {@code updatedAt}
     */
    public boolean updateDetails(String newTitle, String newDescription, TicketPriority newPriority, Clock clock) {
        requireEditable();
        boolean changed = false;
        if (newTitle != null && !newTitle.equals(title)) {
            title = requireText(newTitle, "title");
            changed = true;
        }
        if (newDescription != null && !newDescription.equals(description)) {
            description = requireText(newDescription, "description");
            changed = true;
        }
        if (newPriority != null && newPriority != priority) {
            priority = newPriority;
            changed = true;
        }
        if (changed) {
            touch(clock);
        }
        return changed;
    }

    /** REQ-4: assigns ({@code null} = unassign). Terminal tickets can't be reassigned (A-17). Status is untouched (A-19). */
    public boolean assign(String newAssignee, Clock clock) {
        requireEditable();
        if (Objects.equals(newAssignee, assignee)) {
            return false;
        }
        assignee = newAssignee;
        touch(clock);
        return true;
    }

    /**
     * REQ-11/12: the only status mutator. Illegal moves are rejected before anything changes; legal ones set the
     * matching lifecycle timestamp (A-22) to the same instant as {@code updatedAt}.
     */
    public void changeStatus(TicketStatus target, Clock clock) {
        Objects.requireNonNull(target, "target");
        if (!status.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(id, status, target);
        }
        Instant now = clock.instant();
        status = target;
        updatedAt = now;
        switch (target) {
            case RESOLVED -> resolvedAt = now;
            case CLOSED -> closedAt = now;
            case CANCELLED -> cancelledAt = now;
            case OPEN, IN_PROGRESS -> {
                // no lifecycle timestamp
            }
        }
    }

    /** Comments are accepted unless the ticket is CLOSED or CANCELLED (A-18). */
    public void ensureCommentable() {
        if (status.isTerminal()) {
            throw new TicketNotCommentableException(id, status);
        }
    }

    private void requireEditable() {
        if (status.isTerminal()) {
            throw new TicketNotEditableException(id, status);
        }
    }

    private void touch(Clock clock) {
        Instant now = clock.instant();
        // Never move updatedAt backwards (e.g. clock skew between nodes; review SR-16).
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getAssignee() {
        return assignee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}
