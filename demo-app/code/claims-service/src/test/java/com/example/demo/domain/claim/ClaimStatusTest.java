package com.example.demo.domain.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
@DisplayName("CM-UT-005: claim status transition policy")
class ClaimStatusTest {

    // Independent expected outcomes from TEST_SPECIFICATION.md section 2.3.
    @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
    @CsvSource(textBlock = """
            SUBMITTED,    SUBMITTED,    false
            SUBMITTED,    UNDER_REVIEW, true
            SUBMITTED,    APPROVED,     false
            SUBMITTED,    REJECTED,     true
            SUBMITTED,    CLOSED,       false
            UNDER_REVIEW, SUBMITTED,    true
            UNDER_REVIEW, UNDER_REVIEW, false
            UNDER_REVIEW, APPROVED,     true
            UNDER_REVIEW, REJECTED,     true
            UNDER_REVIEW, CLOSED,       false
            APPROVED,     SUBMITTED,    false
            APPROVED,     UNDER_REVIEW, false
            APPROVED,     APPROVED,     false
            APPROVED,     REJECTED,     false
            APPROVED,     CLOSED,       true
            REJECTED,     SUBMITTED,    false
            REJECTED,     UNDER_REVIEW, false
            REJECTED,     APPROVED,     false
            REJECTED,     REJECTED,     false
            REJECTED,     CLOSED,       true
            CLOSED,       SUBMITTED,    false
            CLOSED,       UNDER_REVIEW, false
            CLOSED,       APPROVED,     false
            CLOSED,       REJECTED,     false
            CLOSED,       CLOSED,       false
            """)
    void followsSpecifiedTransitionMatrix(ClaimStatus current, ClaimStatus target, boolean allowed) {
        assertEquals(allowed, current.canTransitionTo(target),
                () -> "Unexpected transition policy for " + current + " -> " + target);
    }
}
