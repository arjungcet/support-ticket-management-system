package com.supportdesk.ticket.api;

import com.supportdesk.shared.error.FieldError;
import com.supportdesk.shared.error.RequestValidationException;
import java.util.ArrayList;
import java.util.List;

/** Collects every field problem of a request so they are reported together (spec/api-contract.md §1.3). */
final class Validation {

    private final List<FieldError> errors = new ArrayList<>();

    void add(String location, String field, String code, String message) {
        errors.add(new FieldError(location, field, code, message));
    }

    boolean isEmpty() {
        return errors.isEmpty();
    }

    void throwIfInvalid() {
        if (!errors.isEmpty()) {
            throw new RequestValidationException(errors);
        }
    }
}
