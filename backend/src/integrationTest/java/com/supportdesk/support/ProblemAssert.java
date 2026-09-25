package com.supportdesk.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;

/**
 * Assertions for the single error structure in spec/api-contract.md §2 (RFC 9457 + project extensions).
 * Asserts machine-readable parts only — never {@code detail}/{@code title}/{@code message} text (api-contract §8.2).
 */
public final class ProblemAssert {

    private static final MediaType PROBLEM_JSON = MediaType.valueOf("application/problem+json");
    private static final List<String> REQUIRED_PROPERTIES = List.of(
            "type", "title", "status", "detail", "instance", "code", "correlationId", "timestamp", "errors");

    /** One entry of {@code errors[]}: (location, field, code). {@code field} may be {@code null}. */
    public record FieldError(String location, String field, String code) {

        public static FieldError body(String field, String code) {
            return new FieldError("body", field, code);
        }

        public static FieldError query(String field, String code) {
            return new FieldError("query", field, code);
        }

        public static FieldError path(String field, String code) {
            return new FieldError("path", field, code);
        }
    }

    private ProblemAssert() {
    }

    /** Asserts status, problem content type, stable {@code code} and the presence of every required property. */
    public static void assertProblem(ApiResponse response, int status, String code) {
        assertThat(response.status()).as("HTTP status; body=%s", response.rawBody()).isEqualTo(status);
        assertThat(response.contentType()).as("content type").isNotNull();
        assertThat(response.contentType().isCompatibleWith(PROBLEM_JSON))
                .as("content type %s is application/problem+json", response.contentType())
                .isTrue();
        Map<String, Object> body = response.body();
        assertThat(body).as("problem properties").containsKeys(REQUIRED_PROPERTIES.toArray(String[]::new));
        assertThat(body.get("code")).as("error code").isEqualTo(code);
        assertThat(((Number) body.get("status")).intValue()).isEqualTo(status);
        assertThat(response.string("type")).endsWith("/problems/" + code.toLowerCase().replace('_', '-'));
        assertThat(response.string("correlationId")).isEqualTo(response.header("X-Correlation-Id"));
        if (!"VALIDATION_FAILED".equals(code)) {
            assertThat(response.list("errors")).as("errors[] empty for non-validation errors").isEmpty();
        }
    }

    /** Asserts a {@code 400 VALIDATION_FAILED} whose {@code errors[]} is exactly the given set of tuples. */
    public static void assertValidationErrors(ApiResponse response, FieldError... expected) {
        assertProblem(response, 400, "VALIDATION_FAILED");
        assertThat(fieldErrors(response)).as("errors[] (location, field, code)").isEqualTo(Set.of(expected));
    }

    /** Asserts {@code errors[]} contains at least the given tuple (used where other fields may also be reported). */
    public static void assertHasValidationError(ApiResponse response, FieldError expected) {
        assertProblem(response, 400, "VALIDATION_FAILED");
        assertThat(fieldErrors(response)).contains(expected);
    }

    @SuppressWarnings("unchecked")
    public static Set<FieldError> fieldErrors(ApiResponse response) {
        List<Map<String, Object>> errors = (List<Map<String, Object>>) (List<?>) response.list("errors");
        errors.forEach(error -> assertThat(error).containsKeys("location", "field", "code", "message"));
        return errors.stream()
                .map(error -> new FieldError(
                        (String) error.get("location"), (String) error.get("field"), (String) error.get("code")))
                .collect(Collectors.toSet());
    }
}
