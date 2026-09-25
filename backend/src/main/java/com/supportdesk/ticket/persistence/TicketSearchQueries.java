package com.supportdesk.ticket.persistence;

import com.supportdesk.ticket.domain.TicketSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Paged ticket search returning list-row projections (one content query + one count query per page). */
public interface TicketSearchQueries {

    Page<TicketSummaryRow> searchSummaries(TicketSearchCriteria criteria, Pageable pageable);
}
