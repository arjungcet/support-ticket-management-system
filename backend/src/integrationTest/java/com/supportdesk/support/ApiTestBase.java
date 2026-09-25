package com.supportdesk.support;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Base class for black-box API tests: a real server on a random port and an HTTP client.
 *
 * <p>Tests don't rely on an empty database (per-test truncation arrives with the test infrastructure in STEP-09).
 * Assertions about list contents are therefore scoped to tickets the test itself created, usually by embedding a
 * unique {@link #token} in their text and querying with it.
 */
public abstract class ApiTestBase {

    @LocalServerPort
    private int port;

    protected ApiClient api;
    protected TicketApi tickets;
    protected String token;

    @BeforeEach
    void setUpClient() {
        api = new ApiClient(port);
        tickets = new TicketApi(api);
        token = uniqueToken();
    }

    /**
     * A token made only of letters that appear in no search term used by the tests (spec/test-strategy.md §9), so
     * embedding it in seeded text can't create accidental matches.
     */
    private static String uniqueToken() {
        String alphabet = "jqvwx";
        StringBuilder token = new StringBuilder("qx");
        UUID uuid = UUID.randomUUID();
        long bits = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        for (int i = 0; i < 12; i++) {
            token.append(alphabet.charAt((int) Math.floorMod(bits >> (i * 5), (long) alphabet.length())));
        }
        return token.toString();
    }
}
