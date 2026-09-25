package com.supportdesk.shared.error;

import java.util.Map;

/** {@code 413 PAYLOAD_TOO_LARGE}: the request body exceeds the limit of spec/api-contract.md §1.1 (security M-4). */
public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(long maxBytes) {
        super(ErrorCode.PAYLOAD_TOO_LARGE, "The request body must be at most " + maxBytes / 1024 + " KB.", Map.of());
    }
}
