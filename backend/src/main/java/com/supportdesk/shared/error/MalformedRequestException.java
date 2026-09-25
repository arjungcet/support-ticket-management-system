package com.supportdesk.shared.error;

import java.util.Map;

/** {@code 400 MALFORMED_REQUEST}: the body is not a JSON object, or a property has the wrong JSON type. */
public class MalformedRequestException extends DomainException {

    public MalformedRequestException() {
        super(ErrorCode.MALFORMED_REQUEST, "The request body is not valid JSON of the expected shape.", Map.of());
    }
}
