package com.supportdesk.ticket.domain;

/**
 * Maximum lengths after trimming, in characters (Unicode code points). Defined once here; they match the column
 * sizes in the Flyway migrations and spec/data-model.md §7 / spec/api-contract.md §2.3.
 */
public final class FieldLimits {

    public static final int TITLE = 200;
    public static final int DESCRIPTION = 5000;
    public static final int ASSIGNEE = 100;
    public static final int COMMENT_AUTHOR = 100;
    public static final int COMMENT_BODY = 5000;
    public static final int SEARCH_KEYWORD = 100;

    private FieldLimits() {
    }

    public static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
