package com.example.demo.domain.claim;

import com.example.demo.domain.claim.events.ClaimSubmitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

@Tag("unit")
@DisplayName("CM-UT-002/003/004: claim creation field validation")
class ClaimFieldValidationTest {

    private static final UUID CLAIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final LocalDate INCIDENT_DATE = LocalDate.of(2020, 1, 1);
    private static final LocalDate FIXED_TODAY = LocalDate.of(2026, 11, 6);
    private static final String LOCATION = "12 Example Road";
    private static final String DESCRIPTION = "Water damage occurred near the kitchen sink.";
    private static final BigDecimal AMOUNT = new BigDecimal("1234.56");

    @ParameterizedTest(name = "Missing {0} is rejected")
    @EnumSource(RequiredField.class)
    void missingRequiredFieldIdentifiesTheMissingValue(RequiredField field) {
        NullPointerException error = assertThrows(NullPointerException.class, () -> new Claim(
                field == RequiredField.CLAIM_ID ? null : CLAIM_ID,
                field == RequiredField.USER_ID ? null : OWNER_ID,
                field == RequiredField.INCIDENT_DATE ? null : INCIDENT_DATE,
                field == RequiredField.INCIDENT_LOCATION ? null : LOCATION,
                field == RequiredField.DESCRIPTION ? null : DESCRIPTION,
                field == RequiredField.CLAIM_AMOUNT ? null : AMOUNT));

        assertTrue(error.getMessage().contains(field.argumentName), "Null rejection must identify the missing field");
    }

    @ParameterizedTest(name = "Incident date offset {0} days from today: accepted={1}")
    @CsvSource({"-36500, true", "-1, true", "0, true", "1, false", "365, false"})
    void incidentDateIsValidatedAgainstControlledBackendToday(int dayOffset, boolean accepted) {
        LocalDate date = FIXED_TODAY.plusDays(dayOffset);
        // Scoped static mocking controls the existing no-argument clock without changing production code.
        try (MockedStatic<LocalDate> clock = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDate::now).thenReturn(FIXED_TODAY);

            if (accepted) {
                Claim claim = newClaim(date, LOCATION, DESCRIPTION, AMOUNT);
                ClaimSubmitted event = assertInstanceOf(ClaimSubmitted.class, claim.getDomainEvents().getFirst());
                assertAll(
                        () -> assertEquals(date, claim.getIncidentDate()),
                        () -> assertEquals(date, event.incidentDate()),
                        () -> assertEquals(ClaimStatus.SUBMITTED, claim.getStatus())
                );
            } else {
                InvalidClaimException error = assertThrows(InvalidClaimException.class,
                        () -> newClaim(date, LOCATION, DESCRIPTION, AMOUNT));
                assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("future"));
            }
        }
    }

    @ParameterizedTest(name = "Location case {index} is trimmed and retained")
    @MethodSource("validLocations")
    void validLocationIsTrimmedBeforeLengthValidation(String input, String expected) {
        Claim claim = newClaim(INCIDENT_DATE, input, DESCRIPTION, AMOUNT);

        assertAll(
                () -> assertEquals(expected, claim.getIncidentLocation()),
                () -> assertEquals(DESCRIPTION, claim.getDescription()),
                () -> assertEquals(ClaimStatus.SUBMITTED, claim.getStatus()),
                () -> assertEquals(1, claim.getDomainEvents().size())
        );
    }

    @ParameterizedTest(name = "Location case {index} with invalid trimmed length is rejected")
    @MethodSource("invalidLocations")
    void invalidLocationRaisesDomainValidationError(String location) {
        InvalidClaimException error = assertThrows(InvalidClaimException.class,
                () -> newClaim(INCIDENT_DATE, location, DESCRIPTION, AMOUNT));

        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("location"));
    }

    @ParameterizedTest(name = "Description case {index} is trimmed and retained")
    @MethodSource("validDescriptions")
    void validDescriptionIsTrimmedBeforeLengthValidation(String input, String expected) {
        Claim claim = newClaim(INCIDENT_DATE, LOCATION, input, AMOUNT);

        assertAll(
                () -> assertEquals(expected, claim.getDescription()),
                () -> assertEquals(LOCATION, claim.getIncidentLocation()),
                () -> assertEquals(ClaimStatus.SUBMITTED, claim.getStatus()),
                () -> assertEquals(1, claim.getDomainEvents().size())
        );
    }

    @ParameterizedTest(name = "Description case {index} with invalid trimmed length is rejected")
    @MethodSource("invalidDescriptions")
    void invalidDescriptionRaisesDomainValidationError(String description) {
        InvalidClaimException error = assertThrows(InvalidClaimException.class,
                () -> newClaim(INCIDENT_DATE, LOCATION, description, AMOUNT));

        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("description"));
    }

    @ParameterizedTest(name = "Amount {0} meets current domain bounds")
    @ValueSource(strings = {"0.01", "1.00", "999999.99", "1000000", "1000000.00", "1.005", "999999.999"})
    void validAmountIsRetainedInClaimAndSubmissionEvent(String value) {
        BigDecimal amount = new BigDecimal(value);

        Claim claim = newClaim(INCIDENT_DATE, LOCATION, DESCRIPTION, amount);

        ClaimSubmitted event = assertInstanceOf(ClaimSubmitted.class, claim.getDomainEvents().getFirst());
        assertAll(
                () -> assertEquals(amount, claim.getClaimAmount(), "The domain must not silently round the input"),
                () -> assertEquals(amount, event.claimAmount()),
                () -> assertEquals(ClaimStatus.SUBMITTED, claim.getStatus()),
                () -> assertEquals(1, claim.getDomainEvents().size())
        );
    }

    @ParameterizedTest(name = "Out-of-range amount {0} is rejected")
    @ValueSource(strings = {"-0.01", "-1", "-1000000", "0", "0.00", "1000000.01", "1000001"})
    void zeroNegativeOrAboveMaximumAmountRaisesDomainValidationError(String value) {
        InvalidClaimException error = assertThrows(InvalidClaimException.class,
                () -> newClaim(INCIDENT_DATE, LOCATION, DESCRIPTION, new BigDecimal(value)));

        assertTrue(error.getMessage().toLowerCase(Locale.ROOT).contains("amount"));
    }

    @ParameterizedTest(name = "Characterization: domain currently accepts sub-cent amount {0}")
    @ValueSource(strings = {"0.001", "0.009", "0.009999"})
    void currentDomainBehaviorAcceptsPositiveSubCentAmounts(String value) {
        BigDecimal amount = new BigDecimal(value);

        Claim claim = newClaim(INCIDENT_DATE, LOCATION, DESCRIPTION, amount);

        ClaimSubmitted event = assertInstanceOf(ClaimSubmitted.class, claim.getDomainEvents().getFirst());
        assertAll(
                () -> assertEquals(amount, claim.getClaimAmount()),
                () -> assertEquals(amount, event.claimAmount())
        );
    }

    @Tag("contract-gap")
    @ParameterizedTest(name = "Potential defect CL-03: API minimum must reject {0} at the domain boundary")
    @ValueSource(strings = {"0.001", "0.009", "0.009999"})
    void apiMinimumExpectationRejectsPositiveAmountsBelowOneCent(String value) {
        // Both OpenAPI request schemas specify minimum: 0.01; Claim only checks greater than zero.
        // Keep this API-aligned expectation visible, separately from current-domain characterization.
        assertThrows(InvalidClaimException.class,
                () -> newClaim(INCIDENT_DATE, LOCATION, DESCRIPTION, new BigDecimal(value)),
                "API/domain minimum discrepancy: both request contracts require >= 0.01, input=" + value);
    }

    private static Stream<Arguments> validLocations() {
        return Stream.of(
                Arguments.of("L".repeat(5), "L".repeat(5)),
                Arguments.of("L".repeat(200), "L".repeat(200)),
                Arguments.of(" \t" + "L".repeat(5) + "\r\n ", "L".repeat(5)),
                Arguments.of("   " + "L".repeat(200) + "   ", "L".repeat(200)),
                Arguments.of("\t12  Example Road\n", "12  Example Road")
        );
    }

    private static Stream<String> invalidLocations() {
        return Stream.of("", "     ", "\t\r\n", "L".repeat(4), "L".repeat(201),
                "   " + "L".repeat(4) + "   ", "   " + "L".repeat(201) + "   ");
    }

    private static Stream<Arguments> validDescriptions() {
        return Stream.of(
                Arguments.of("D".repeat(10), "D".repeat(10)),
                Arguments.of("D".repeat(1000), "D".repeat(1000)),
                Arguments.of(" \t" + "D".repeat(10) + "\r\n ", "D".repeat(10)),
                Arguments.of("   " + "D".repeat(1000) + "   ", "D".repeat(1000)),
                Arguments.of("\tWater  damage in kitchen\n", "Water  damage in kitchen")
        );
    }

    private static Stream<String> invalidDescriptions() {
        return Stream.of("", "          ", "\t\r\n", "D".repeat(9), "D".repeat(1001),
                "   " + "D".repeat(9) + "   ", "   " + "D".repeat(1001) + "   ");
    }

    private static Claim newClaim(LocalDate date, String location, String description, BigDecimal amount) {
        return new Claim(CLAIM_ID, OWNER_ID, date, location, description, amount);
    }

    private enum RequiredField {
        CLAIM_ID("claimId"),
        USER_ID("userId"),
        INCIDENT_DATE("incidentDate"),
        INCIDENT_LOCATION("incidentLocation"),
        DESCRIPTION("description"),
        CLAIM_AMOUNT("claimAmount");

        private final String argumentName;

        RequiredField(String argumentName) {
            this.argumentName = argumentName;
        }
    }
}
