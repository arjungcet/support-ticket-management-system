package com.supportdesk.shared.error;

/**
 * One entry of {@code errors[]} (spec/api-contract.md §2, §2.2). {@code field} is {@code null} for object-level
 * rules. The submitted value is never included.
 */
public record FieldError(String location, String field, String code, String message) {

    public static final String BODY = "body";
    public static final String QUERY = "query";
    public static final String PATH = "path";
}
