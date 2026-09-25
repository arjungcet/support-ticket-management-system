package com.supportdesk.ticket.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Keyword search + status filter + sort (REQ-6, REQ-7; spec/api-contract.md §6.2). {@code keyword} is trimmed and
 * may be empty (no keyword filter); {@code statuses} empty means all statuses.
 */
public record TicketSearchCriteria(String keyword, Set<TicketStatus> statuses, SortField sortField, boolean ascending) {

    public enum SortField {
        CREATED_AT("createdAt"),
        UPDATED_AT("updatedAt"),
        PRIORITY("priority"),
        STATUS("status");

        private final String apiName;

        SortField(String apiName) {
            this.apiName = apiName;
        }

        public String apiName() {
            return apiName;
        }
    }

    public TicketSearchCriteria {
        statuses = Set.copyOf(statuses);
    }

    public boolean hasKeyword() {
        return !keyword.isEmpty();
    }

    /**
     * LIKE pattern for a case-insensitive, literal substring match: {@code \}, {@code %} and {@code _} typed by the
     * user are escaped with {@code \} (spec/data-model.md §13.1).
     */
    public String likePattern() {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
