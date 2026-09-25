package com.supportdesk.shared.error;

import java.util.Map;

/**
 * Base of every expected, client-visible failure. Carries the stable {@link ErrorCode}, a user-safe {@code detail}
 * and the extension properties defined for that code in spec/api-contract.md §2.1.
 */
public abstract class DomainException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> extensions;

    protected DomainException(ErrorCode errorCode, String detail, Map<String, Object> extensions) {
        super(detail);
        this.errorCode = errorCode;
        this.extensions = Map.copyOf(extensions);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return getMessage();
    }

    public Map<String, Object> extensions() {
        return extensions;
    }
}
