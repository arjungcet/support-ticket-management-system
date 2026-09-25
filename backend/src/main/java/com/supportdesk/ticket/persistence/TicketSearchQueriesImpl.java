package com.supportdesk.ticket.persistence;

import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class TicketSearchQueriesImpl implements TicketSearchQueries {

    private final EntityManager entityManager;

    TicketSearchQueriesImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<TicketSummaryRow> searchSummaries(TicketSearchCriteria criteria, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<TicketSummaryRow> query = cb.createQuery(TicketSummaryRow.class);
        Root<Ticket> root = query.from(Ticket.class);
        query.select(cb.construct(TicketSummaryRow.class, root.get("id"), root.get("title"), root.get("priority"),
                        root.get("status"), root.get("assignee"), root.get("createdAt"), root.get("updatedAt")))
                .where(TicketSpecifications.matching(criteria, root, cb))
                .orderBy(TicketSpecifications.ordering(criteria, root, cb));
        List<TicketSummaryRow> content = entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<Ticket> countRoot = count.from(Ticket.class);
        count.select(cb.count(countRoot)).where(TicketSpecifications.matching(criteria, countRoot, cb));
        long total = entityManager.createQuery(count).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }
}
