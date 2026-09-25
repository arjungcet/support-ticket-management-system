package com.supportdesk.ticket.domain;

import java.util.List;

/**
 * Ticket lifecycle. The transition table below is the single source of truth for which moves are legal
 * (spec/state-machine.md §3–§4): every other (from, to) pair — including self-transitions and anything leaving a
 * terminal state — is rejected. Declaration order is the lifecycle order used for sorting.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    CANCELLED;

    /** Allowed targets in the order the API returns them as {@code allowedTransitions}. */
    public List<TicketStatus> allowedTargets() {
        return switch (this) {
            case OPEN -> List.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> List.of(RESOLVED, CANCELLED);
            case RESOLVED -> List.of(CLOSED);
            case CLOSED, CANCELLED -> List.of();
        };
    }

    public boolean canTransitionTo(TicketStatus target) {
        return allowedTargets().contains(target);
    }

    /** CLOSED and CANCELLED: no outgoing transitions; details, assignee and comments are frozen (A-17, A-18). */
    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }
}
