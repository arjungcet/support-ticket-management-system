package com.supportdesk.ticket.api;

import static com.supportdesk.support.ApiClient.encode;
import static com.supportdesk.support.ApiClient.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.support.ApiResponse;
import com.supportdesk.support.ApiTestBase;
import com.supportdesk.support.SpecAcceptanceTest;
import com.supportdesk.support.TicketApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * REQ-6 — spec/api-contract.md §6.2.1, spec/test-strategy.md §9 (TS-SRCH-01…17).
 *
 * <p>Seed data is the §9 table plus two decoys (review finding SR-15): D1 would match an unescaped {@code 100%} and D2
 * an unescaped {@code code_2026}, so the escaping tests can actually fail. Every seeded ticket carries the test's unique
 * token; assertions compare results restricted to the seeded tickets.
 */
@SpecAcceptanceTest
@DisplayName("REQ-6 Search tickets")
class TicketSearchApiIT extends ApiTestBase {

    /** Label → id of the tickets seeded for this test. */
    private final Map<String, Long> seeded = new LinkedHashMap<>();

    @BeforeEach
    void seed() {
        seed("1", "OPEN", "Login fails on Portal", "403 after reset", null);
        seed("2", "IN_PROGRESS", "Printer jam", "Paper stuck; see LOGIN screen photo", null);
        seed("3", "RESOLVED", "Discount 100% not applied", "Promo code_2026 rejected", null);
        seed("4", "CLOSED", "Path C:\\temp missing", "User's folder gone", null);
        seed("5", "CANCELLED", "Café menu broken", "Unicode ümlaut test", null);
        seed("6", "OPEN", "Unrelated", "Nothing to see", "wombat.keeper");
        seed("7", "OPEN", "Log in button misaligned", "CSS issue", null);
        seed("D1", "OPEN", "Order 1000 items", "Bulk order", null);
        seed("D2", "OPEN", "Promo codeX2026 expired", "Different promo", null);
    }

    private void seed(String label, String status, String title, String description, String assignee) {
        ApiResponse ticket = tickets.createInStatus(status, title, description + " " + token);
        if (assignee != null) {
            ApiResponse assigned = api.put(TicketApi.assignee(ticket.number("id")),
                    json("version", ticket.number("version"), "assignee", assignee));
            assertThat(assigned.status()).isEqualTo(200);
        }
        seeded.put(label, ticket.number("id"));
    }

    /** Runs a search and returns the labels of the seeded tickets in the result (all pages). */
    private Set<String> search(String extraQuery) {
        Set<Long> resultIds = Set.copyOf(tickets.listAllIds("?" + extraQuery));
        return seeded.entrySet().stream()
                .filter(entry -> resultIds.contains(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Set<String> searchQ(String keyword) {
        return search("q=" + encode(keyword));
    }

    @Test
    @DisplayName("TS-SRCH-01 matches title and description case-insensitively")
    void login_matchesTitleAndDescription() {
        assertThat(searchQ("login")).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("TS-SRCH-02 the case of the query is irrelevant")
    void upperCaseQuery_matchesSame() {
        assertThat(searchQ("LOGIN")).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    @DisplayName("TS-SRCH-03 matches substrings, not just words or prefixes")
    void substring_matches() {
        assertThat(searchQ("ortal")).containsExactly("1");
    }

    @Test
    @DisplayName("TS-SRCH-04 '%' is matched literally (decoy 'Order 1000 items' excluded)")
    void percent_isLiteral() {
        assertThat(searchQ("100%")).containsExactly("3");
    }

    @Test
    @DisplayName("TS-SRCH-05 '_' is matched literally (decoy 'codeX2026' excluded)")
    void underscore_isLiteral() {
        assertThat(searchQ("code_2026")).containsExactly("3");
    }

    @Test
    @DisplayName("TS-SRCH-06 backslash is matched literally")
    void backslash_isLiteral() {
        assertThat(searchQ("C:\\temp")).containsExactly("4");
    }

    @Test
    @DisplayName("TS-SRCH-07 quotes are safe and matched literally")
    void quote_isLiteral() {
        assertThat(searchQ("user's")).containsExactly("4");
    }

    @ParameterizedTest(name = "q={0}")
    @ValueSource(strings = {"café", "CAFÉ"})
    @DisplayName("TS-SRCH-08 non-ASCII text is case-folded (depends on DB collation — review SR-05)")
    void nonAscii_isCaseInsensitive(String keyword) {
        assertThat(searchQ(keyword)).containsExactly("5");
    }

    @Test
    @DisplayName("TS-SRCH-09 a multi-word query is one phrase (API-1)")
    void multiWord_isPhrase() {
        assertThat(searchQ("log in")).containsExactly("7");
    }

    @Test
    @DisplayName("TS-SRCH-10 no match returns 200 with an empty page")
    void noMatch_isEmptyPage() {
        assertThat(searchQ("zzz")).isEmpty();

        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "zzz");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.content()).isEmpty();
        assertThat(response.page()).containsEntry("totalElements", 0).containsEntry("totalPages", 0);
    }

    @Test
    @DisplayName("TS-SRCH-11 empty, whitespace-only or absent q is ignored")
    void emptyQuery_isIgnored() {
        Set<String> all = seeded.keySet();

        assertThat(search("q=")).containsExactlyInAnyOrderElementsOf(all);
        assertThat(search("q=" + encode("   "))).containsExactlyInAnyOrderElementsOf(all);
        assertThat(search("")).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    @DisplayName("TS-SRCH-12 a 100-character keyword is accepted")
    void maxLengthKeyword_isAccepted() {
        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + "a".repeat(100));

        assertThat(response.status()).isEqualTo(200);
    }

    @Test
    @DisplayName("TS-SRCH-13 comments are not searched (A-24)")
    void commentText_isNotSearched() {
        tickets.addComment(seeded.get("6"), "sam.ops", "zebrafish mentioned only here");

        assertThat(searchQ("zebrafish")).isEmpty();
    }

    @Test
    @DisplayName("TS-SRCH-14 assignee is not searched (A-24)")
    void assignee_isNotSearched() {
        assertThat(searchQ("wombat")).isEmpty();
    }

    @Test
    @DisplayName("TS-SRCH-15 keyword and status filter combine with AND")
    void keywordAndStatus_combine() {
        assertThat(search("q=login&status=OPEN")).containsExactly("1");
    }

    @Test
    @DisplayName("TS-SRCH-16 paging through results is complete, without duplicates, with correct totals")
    void paging_isStable() {
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + token + "&size=2&page=" + page);
            assertThat(response.page()).containsEntry("totalElements", 9).containsEntry("totalPages", 5);
            seen.addAll(response.contentIds());
        }

        assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(seeded.values());
    }

    @Test
    @DisplayName("TS-SRCH-17 SQL-injection-like input is a literal string")
    void injection_isLiteral() {
        ApiResponse response = api.get(TicketApi.TICKETS + "?q=" + encode("' OR 1=1 --"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(searchQ("' OR 1=1 --")).isEmpty();
    }
}
