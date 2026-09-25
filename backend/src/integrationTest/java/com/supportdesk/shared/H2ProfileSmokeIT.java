package com.supportdesk.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.TicketApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * TS-INT-08: the {@code h2} profile (no Docker) starts, the portable migrations apply, and the basics work. Proves
 * portability only — database behaviour is tested on PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("h2")
class H2ProfileSmokeIT extends ApiTestBase {

    @Test
    @DisplayName("create, get and search work on the h2 profile")
    void basicsWork() {
        ApiResponse created = tickets.create("H2 smoke " + token, "portable migrations");

        ApiResponse fetched = tickets.get(created.number("id"));

        assertThat(fetched.body()).isEqualTo(created.body());
        assertThat(tickets.listAllIds("?q=" + token)).containsExactly(created.number("id"));
    }
}
