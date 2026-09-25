package com.supportdesk.ticket.domain;

/** Ticket priority (A-15). Declaration order is the rank used for sorting: LOW &lt; MEDIUM &lt; HIGH &lt; URGENT. */
public enum TicketPriority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
