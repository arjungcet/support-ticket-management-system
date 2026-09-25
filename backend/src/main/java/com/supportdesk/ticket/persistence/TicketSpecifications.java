package com.supportdesk.ticket.persistence;

import com.supportdesk.ticket.domain.Ticket;
import com.supportdesk.ticket.domain.TicketPriority;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import com.supportdesk.ticket.domain.TicketStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;

/**
 * Search, filter and ordering for the ticket list (spec/data-model.md §13). User input is only ever a bound
 * parameter. Priority and status are ordered by rank via CASE (not alphabetically), always followed by {@code id} in
 * the same direction so pagination is stable.
 */
final class TicketSpecifications {

    private static final char ESCAPE = '\\';

    private TicketSpecifications() {
    }

    static Predicate matching(TicketSearchCriteria criteria, Root<Ticket> root, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.hasKeyword()) {
            String pattern = criteria.likePattern();
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("description")), pattern, ESCAPE)));
        }
        if (!criteria.statuses().isEmpty()) {
            predicates.add(root.get("status").in(criteria.statuses()));
        }
        return cb.and(predicates.toArray(Predicate[]::new));
    }

    static List<Order> ordering(TicketSearchCriteria criteria, Root<Ticket> root, CriteriaBuilder cb) {
        Expression<?> sortKey = sortKey(criteria.sortField(), root, cb);
        return criteria.ascending()
                ? List.of(cb.asc(sortKey), cb.asc(root.get("id")))
                : List.of(cb.desc(sortKey), cb.desc(root.get("id")));
    }

    private static Expression<?> sortKey(TicketSearchCriteria.SortField field, Root<Ticket> root, CriteriaBuilder cb) {
        return switch (field) {
            case CREATED_AT -> root.get("createdAt");
            case UPDATED_AT -> root.get("updatedAt");
            case PRIORITY -> rank(cb, root.get("priority"), TicketPriority.values());
            case STATUS -> rank(cb, root.get("status"), TicketStatus.values());
        };
    }

    private static <E extends Enum<E>> Expression<Integer> rank(CriteriaBuilder cb, Expression<E> column, E[] values) {
        CriteriaBuilder.SimpleCase<E, Integer> ranked = cb.selectCase(column);
        for (E value : values) {
            ranked = ranked.when(value, value.ordinal());
        }
        return ranked.otherwise(values.length);
    }
}
