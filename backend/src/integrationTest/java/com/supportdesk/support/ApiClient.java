package com.supportdesk.support;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Minimal black-box HTTP client for API tests. Never throws on 4xx/5xx — every response is returned for assertion.
 * Tests talk to the running server over real HTTP and know nothing about controllers, services or entities.
 */
public final class ApiClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String baseUrl;
    private final RestClient rest;

    public ApiClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.rest = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    /** Builds a JSON object from key/value pairs; values may be {@code null} (unlike {@code Map.of}). */
    public static Map<String, Object> json(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs expected");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    /** URL-encodes a query-parameter value. Paths passed to this client are sent exactly as given. */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public ApiResponse get(String path) {
        return exchange(HttpMethod.GET, path, null, null, HttpHeaders.EMPTY);
    }

    public ApiResponse post(String path, Object body) {
        return exchange(HttpMethod.POST, path, toJson(body), MediaType.APPLICATION_JSON, HttpHeaders.EMPTY);
    }

    public ApiResponse patch(String path, Object body) {
        return exchange(HttpMethod.PATCH, path, toJson(body), MediaType.APPLICATION_JSON, HttpHeaders.EMPTY);
    }

    public ApiResponse put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, toJson(body), MediaType.APPLICATION_JSON, HttpHeaders.EMPTY);
    }

    /** Sends a raw body string (e.g. malformed JSON) with the given content type. */
    public ApiResponse raw(HttpMethod method, String path, String body, MediaType contentType) {
        return exchange(method, path, body, contentType, HttpHeaders.EMPTY);
    }

    public ApiResponse exchange(HttpMethod method, String path, String body, MediaType contentType,
            HttpHeaders extraHeaders) {
        RestClient.RequestBodySpec spec = rest.method(method)
                .uri(URI.create(baseUrl + path))
                .headers(headers -> headers.addAll(extraHeaders));
        if (body != null) {
            spec = spec.contentType(contentType).body(body);
        }
        ResponseEntity<String> entity = spec.retrieve().toEntity(String.class);
        String raw = entity.getBody();
        return new ApiResponse(entity.getStatusCode().value(), entity.getHeaders(), raw, parse(raw));
    }

    private static String toJson(Object body) {
        return JSON.writeValueAsString(body);
    }

    private static Object parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(raw, Object.class);
        } catch (RuntimeException notJson) {
            return null;
        }
    }
}
