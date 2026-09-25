package com.supportdesk.shared.error;

import java.util.Locale;

/** Stable error codes of spec/api-contract.md §2.1. Clients branch on {@link #name()}, never on message text. */
public enum ErrorCode {
    MALFORMED_REQUEST(400, "Malformed request"),
    VALIDATION_FAILED(400, "Validation failed"),
    TICKET_NOT_FOUND(404, "Ticket not found"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    TICKET_CONCURRENT_MODIFICATION(409, "Ticket was modified"),
    TICKET_INVALID_TRANSITION(409, "Invalid status transition"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    TICKET_NOT_EDITABLE(422, "Ticket cannot be edited"),
    TICKET_NOT_COMMENTABLE(422, "Ticket cannot be commented on"),
    INTERNAL_ERROR(500, "Internal server error");

    private static final String TYPE_BASE = "https://supportdesk.example/problems/";

    private final int httpStatus;
    private final String title;

    ErrorCode(int httpStatus, String title) {
        this.httpStatus = httpStatus;
        this.title = title;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }

    public String type() {
        return TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
