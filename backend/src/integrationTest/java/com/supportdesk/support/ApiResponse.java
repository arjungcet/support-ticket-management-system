package com.supportdesk.support;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * An HTTP response as seen by a black-box client: status, headers and the parsed JSON body (or {@code null}).
 */
public record ApiResponse(int status, HttpHeaders headers, String rawBody, Object json) {

    @SuppressWarnings("unchecked")
    public Map<String, Object> body() {
        if (!(json instanceof Map)) {
            throw new AssertionError("Expected a JSON object body but got: " + rawBody);
        }
        return (Map<String, Object>) json;
    }

    public Object get(String property) {
        return body().get(property);
    }

    public String string(String property) {
        return (String) get(property);
    }

    public long number(String property) {
        return ((Number) get(property)).longValue();
    }

    public Instant instant(String property) {
        String value = string(property);
        return value == null ? null : Instant.parse(value);
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(String property) {
        return (List<Object>) get(property);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> content() {
        return (List<Map<String, Object>>) get("content");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> page() {
        return (Map<String, Object>) get("page");
    }

    public List<Long> contentIds() {
        return content().stream().map(item -> ((Number) item.get("id")).longValue()).toList();
    }

    public MediaType contentType() {
        return headers.getContentType();
    }

    public String header(String name) {
        return headers.getFirst(name);
    }
}
