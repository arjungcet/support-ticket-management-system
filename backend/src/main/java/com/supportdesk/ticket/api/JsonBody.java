package com.supportdesk.ticket.api;

import com.supportdesk.shared.error.FieldError;
import com.supportdesk.shared.error.MalformedRequestException;
import com.supportdesk.ticket.domain.FieldLimits;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads a request body with the exact semantics of spec/api-contract.md §1–§2, which plain DTO binding can't express
 * (review SR-02/SR-03): absent vs explicit {@code null}, strict JSON types (no coercion; wrong type →
 * {@code MALFORMED_REQUEST}), unknown properties reported as {@code UNKNOWN_FIELD}, and all field errors collected
 * together. Text is trimmed before validation; lengths count characters after trimming.
 */
final class JsonBody {

    /** How a text or enum property may be supplied. */
    enum Presence {
        /** Must be present and non-null. */
        REQUIRED,
        /** May be absent (unchanged); explicit {@code null} is an error. */
        OPTIONAL,
        /** May be absent or {@code null}; for text, blank also means "no value". */
        OPTIONAL_CLEARABLE,
        /** Key must be present; {@code null} or blank means "no value" (API-3). */
        REQUIRED_KEY_CLEARABLE
    }

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final ObjectNode node;
    private final Validation validation;

    private JsonBody(ObjectNode node, Validation validation) {
        this.node = node;
        this.validation = validation;
    }

    /** Parses the body; anything that isn't a single JSON object is {@code 400 MALFORMED_REQUEST}. */
    static JsonBody parse(byte[] raw, Validation validation, String... allowedFields) {
        if (raw == null || raw.length == 0) {
            throw new MalformedRequestException();
        }
        JsonNode parsed;
        try {
            parsed = MAPPER.readTree(new String(raw, StandardCharsets.UTF_8));
        } catch (JacksonException e) {
            throw new MalformedRequestException();
        }
        if (!(parsed instanceof ObjectNode object)) {
            throw new MalformedRequestException();
        }
        List<String> allowed = Arrays.asList(allowedFields);
        for (String name : object.propertyNames()) {
            if (!allowed.contains(name)) {
                validation.add(FieldError.BODY, name, "UNKNOWN_FIELD", "Unknown field '" + name + "'.");
            }
        }
        return new JsonBody(object, validation);
    }

    boolean hasAny(String... fields) {
        return Arrays.stream(fields).anyMatch(node::has);
    }

    /** Optimistic-locking version: required non-negative integer. */
    Long version() {
        JsonNode value = node.get("version");
        if (value == null || value.isNull()) {
            validation.add(FieldError.BODY, "version", "REQUIRED", "Version is required.");
            return null;
        }
        if (!value.isNumber()) {
            throw new MalformedRequestException();
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            validation.add(FieldError.BODY, "version", "INVALID_VALUE", "Version must be a non-negative integer.");
            return null;
        }
        return value.longValue();
    }

    /** A text property, trimmed. Returns {@code null} when absent, cleared, or invalid (see {@link Presence}). */
    String text(String field, int maxLength, Presence presence) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            boolean missing = value == null
                    ? presence == Presence.REQUIRED || presence == Presence.REQUIRED_KEY_CLEARABLE
                    : presence == Presence.REQUIRED || presence == Presence.OPTIONAL;
            if (missing) {
                validation.add(FieldError.BODY, field, "REQUIRED", label(field) + " is required.");
            }
            return null;
        }
        if (!value.isString()) {
            throw new MalformedRequestException();
        }
        String trimmed = value.stringValue().strip();
        if (trimmed.isEmpty()) {
            if (presence == Presence.REQUIRED || presence == Presence.OPTIONAL) {
                validation.add(FieldError.BODY, field, "BLANK", label(field) + " must not be blank.");
            }
            return null;
        }
        if (FieldLimits.length(trimmed) > maxLength) {
            validation.add(FieldError.BODY, field, "TOO_LONG",
                    label(field) + " must be at most " + maxLength + " characters.");
            return null;
        }
        return trimmed;
    }

    /** An enum property given by its exact constant name (case-sensitive). */
    <E extends Enum<E>> E enumValue(String field, Class<E> type, Presence presence) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            boolean missing = value == null
                    ? presence == Presence.REQUIRED || presence == Presence.REQUIRED_KEY_CLEARABLE
                    : presence != Presence.OPTIONAL_CLEARABLE;
            if (missing) {
                validation.add(FieldError.BODY, field, "REQUIRED", label(field) + " is required.");
            }
            return null;
        }
        if (!value.isString()) {
            throw new MalformedRequestException();
        }
        String name = value.stringValue();
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        validation.add(FieldError.BODY, field, "INVALID_VALUE",
                label(field) + " must be one of " + allowedNames(type) + ".");
        return null;
    }

    static <E extends Enum<E>> String allowedNames(Class<E> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }

    static String label(String field) {
        String spaced = field.replaceAll("([A-Z])", " $1").toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
