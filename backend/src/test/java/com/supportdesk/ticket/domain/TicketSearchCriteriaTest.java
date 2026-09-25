package com.supportdesk.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.supportdesk.ticket.domain.TicketSearchCriteria.SortField;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** TS-UNIT-08: the user's LIKE wildcards are escaped so they match literally (spec/data-model.md §13.1). */
class TicketSearchCriteriaTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Login|%login%",
            "100%|%100\\%%",
            "code_2026|%code\\_2026%",
            "C:\\temp|%c:\\\\temp%",
            "user's|%user's%"})
    void likePattern_escapesWildcards(String keyword, String expected) {
        TicketSearchCriteria criteria = new TicketSearchCriteria(keyword, Set.of(), SortField.CREATED_AT, false);

        assertThat(criteria.likePattern()).isEqualTo(expected);
    }
}
