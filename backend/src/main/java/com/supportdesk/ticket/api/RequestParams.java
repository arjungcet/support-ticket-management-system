package com.supportdesk.ticket.api;

import com.supportdesk.shared.error.FieldError;
import com.supportdesk.ticket.domain.FieldLimits;
import com.supportdesk.ticket.domain.TicketSearchCriteria;
import com.supportdesk.ticket.domain.TicketSearchCriteria.SortField;
import com.supportdesk.ticket.domain.TicketStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Path and query parameter validation (spec/api-contract.md §5.1, §6.2, §6.3a). */
final class RequestParams {

    static final int DEFAULT_TICKET_PAGE_SIZE = 20;
    static final int DEFAULT_COMMENT_PAGE_SIZE = 50;
    static final int MAX_PAGE_SIZE = 100;

    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern NON_NEGATIVE_INT = Pattern.compile("[0-9]{1,9}");

    private RequestParams() {
    }

    /** A ticket id: an integer ≥ 1 that fits in int64. Returns -1 (and records an error) otherwise. */
    static long ticketId(String raw, Validation validation) {
        if (raw != null && POSITIVE_ID.matcher(raw).matches()) {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException overflow) {
                // falls through to the error below
            }
        }
        validation.add(FieldError.PATH, "ticketId", "INVALID_VALUE", "Ticket id must be a positive integer.");
        return -1;
    }

    static int page(String raw, Validation validation) {
        return intParam(raw, "page", 0, 0, Integer.MAX_VALUE, "Page must be a non-negative integer.", validation);
    }

    static int size(String raw, int defaultSize, Validation validation) {
        return intParam(raw, "size", defaultSize, 1, MAX_PAGE_SIZE,
                "Size must be an integer between 1 and " + MAX_PAGE_SIZE + ".", validation);
    }

    /** Trimmed keyword; empty means "no keyword filter". */
    static String keyword(String raw, Validation validation) {
        String keyword = raw == null ? "" : raw.strip();
        if (JsonBody.containsControlCharacter(keyword, false)) {
            validation.add(FieldError.QUERY, "q", "INVALID_VALUE",
                    "The search keyword must not contain control characters.");
        } else if (FieldLimits.length(keyword) > FieldLimits.SEARCH_KEYWORD) {
            validation.add(FieldError.QUERY, "q", "TOO_LONG",
                    "The search keyword must be at most " + FieldLimits.SEARCH_KEYWORD + " characters.");
        }
        return keyword;
    }

    /** Repeated and/or comma-separated statuses; duplicates are ignored (spec/api-contract.md §6.2.2). */
    static Set<TicketStatus> statuses(List<String> raw, Validation validation) {
        Set<TicketStatus> statuses = EnumSet.noneOf(TicketStatus.class);
        if (raw == null) {
            return statuses;
        }
        boolean invalid = false;
        for (String token : raw.stream().flatMap(value -> Arrays.stream(value.split(","))).toList()) {
            String name = token.strip();
            if (name.isEmpty()) {
                continue;
            }
            Optional<TicketStatus> status = Arrays.stream(TicketStatus.values())
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst();
            status.ifPresent(statuses::add);
            invalid |= status.isEmpty();
        }
        if (invalid) {
            validation.add(FieldError.QUERY, "status", "INVALID_VALUE",
                    "Status must be one of " + JsonBody.allowedNames(TicketStatus.class) + ".");
        }
        return statuses;
    }

    /** {@code field[,asc|desc]} with an allow-listed field; default {@code createdAt,desc}. */
    static TicketSearchCriteria criteria(String keyword, Set<TicketStatus> statuses, String rawSort,
            Validation validation) {
        if (rawSort == null) {
            return new TicketSearchCriteria(keyword, statuses, SortField.CREATED_AT, false);
        }
        String[] parts = rawSort.split(",", -1);
        Optional<SortField> field = Arrays.stream(SortField.values())
                .filter(candidate -> candidate.apiName().equals(parts[0].strip()))
                .findFirst();
        String direction = parts.length > 1 ? parts[1].strip() : "asc";
        if (field.isEmpty() || parts.length > 2 || !(direction.equals("asc") || direction.equals("desc"))) {
            validation.add(FieldError.QUERY, "sort", "INVALID_VALUE",
                    "Sort must be createdAt, updatedAt, priority or status, optionally followed by ,asc or ,desc.");
            return new TicketSearchCriteria(keyword, statuses, SortField.CREATED_AT, false);
        }
        return new TicketSearchCriteria(keyword, statuses, field.get(), direction.equals("asc"));
    }

    private static int intParam(String raw, String name, int defaultValue, int min, int max, String message,
            Validation validation) {
        if (raw == null) {
            return defaultValue;
        }
        if (NON_NEGATIVE_INT.matcher(raw).matches()) {
            int value = Integer.parseInt(raw);
            if (value >= min && value <= max) {
                return value;
            }
        }
        validation.add(FieldError.QUERY, name, "INVALID_VALUE", message);
        return defaultValue;
    }
}
