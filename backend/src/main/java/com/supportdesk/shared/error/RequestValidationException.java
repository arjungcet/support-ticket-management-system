package com.supportdesk.shared.error;

import java.util.List;
import java.util.Map;

/** {@code 400 VALIDATION_FAILED} with every field problem of the request reported together. */
public class RequestValidationException extends DomainException {

    private final List<FieldError> errors;

    public RequestValidationException(List<FieldError> errors) {
        super(ErrorCode.VALIDATION_FAILED, "The request contains " + errors.size() + " invalid field(s).", Map.of());
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
