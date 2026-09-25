package com.supportdesk.ticket.domain;

import com.supportdesk.shared.error.DomainException;
import com.supportdesk.shared.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Business exceptions of the ticket domain, with the extension properties of spec/api-contract.md §2.1. */
public final class TicketExceptions {

    private TicketExceptions() {
    }

    /** Extension map that omits {@code null} values (e.g. the id of a ticket not saved yet). */
    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }

    private static String ticket(Long ticketId) {
        return ticketId == null ? "The ticket" : "Ticket " + ticketId;
    }

    public static class TicketNotFoundException extends DomainException {
        public TicketNotFoundException(long ticketId) {
            super(ErrorCode.TICKET_NOT_FOUND, "Ticket " + ticketId + " was not found.",
                    props("ticketId", ticketId));
        }
    }

    public static class ConcurrentModificationException extends DomainException {
        public ConcurrentModificationException(Long ticketId, long currentVersion) {
            super(ErrorCode.TICKET_CONCURRENT_MODIFICATION,
                    ticket(ticketId) + " was modified by someone else. Reload it and try again.",
                    props("ticketId", ticketId, "currentVersion", currentVersion));
        }
    }

    public static class InvalidStatusTransitionException extends DomainException {

        private final TicketStatus currentStatus;
        private final TicketStatus targetStatus;

        public InvalidStatusTransitionException(Long ticketId, TicketStatus current, TicketStatus target) {
            super(ErrorCode.TICKET_INVALID_TRANSITION,
                    ticket(ticketId) + " cannot move from " + current + " to " + target + ".",
                    props("ticketId", ticketId, "currentStatus", current, "targetStatus", target,
                            "allowedTransitions", current.allowedTargets()));
            this.currentStatus = current;
            this.targetStatus = target;
        }

        public TicketStatus currentStatus() {
            return currentStatus;
        }

        public TicketStatus targetStatus() {
            return targetStatus;
        }
    }

    public static class TicketNotEditableException extends DomainException {
        public TicketNotEditableException(Long ticketId, TicketStatus current) {
            super(ErrorCode.TICKET_NOT_EDITABLE, ticket(ticketId) + " is " + current + " and can no longer be edited.",
                    props("ticketId", ticketId, "currentStatus", current));
        }
    }

    public static class TicketNotCommentableException extends DomainException {
        public TicketNotCommentableException(Long ticketId, TicketStatus current) {
            super(ErrorCode.TICKET_NOT_COMMENTABLE,
                    ticket(ticketId) + " is " + current + " and can no longer receive comments.",
                    props("ticketId", ticketId, "currentStatus", current));
        }
    }
}
