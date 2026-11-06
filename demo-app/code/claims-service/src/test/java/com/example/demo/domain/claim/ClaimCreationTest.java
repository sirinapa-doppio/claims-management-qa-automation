package com.example.demo.domain.claim;

import com.example.demo.domain.DomainEvent;
import com.example.demo.domain.claim.events.ClaimSubmitted;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
@DisplayName("CM-UT-001: successful claim creation and submission events")
class ClaimCreationTest {

    private static final UUID CLAIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_CLAIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final LocalDate INCIDENT_DATE = LocalDate.of(2020, 1, 1);
    private static final String LOCATION = "12 Example Road";
    private static final String DESCRIPTION = "Water damage occurred near the kitchen sink.";
    private static final BigDecimal AMOUNT = new BigDecimal("1234.56");

    @Test
    void validClaimStartsSubmittedWithExactlyOneMatchingEvent() {
        Instant before = Instant.now();

        Claim claim = newClaim(CLAIM_ID);

        Instant after = Instant.now();
        assertCreatedBusinessData(claim, CLAIM_ID);
        assertEquals(claim.getCreatedAt(), claim.getUpdatedAt());
        assertWithinOperation(claim.getCreatedAt(), before, after);
        assertEquals(1, claim.getDomainEvents().size(), "Creation must emit exactly one submission event");
        ClaimSubmitted event = assertInstanceOf(ClaimSubmitted.class, claim.getDomainEvents().getFirst());
        assertAll(
                () -> assertNotNull(event.eventId()),
                () -> assertFalse(event.eventId().isBlank()),
                () -> assertEquals(CLAIM_ID, event.claimId()),
                () -> assertEquals(OWNER_ID, event.userId()),
                () -> assertEquals(INCIDENT_DATE, event.incidentDate()),
                () -> assertEquals(AMOUNT, event.claimAmount()),
                () -> assertWithinOperation(event.occurredAt(), before, after)
        );
    }

    @Test
    void clearingSubmissionEventsDoesNotChangeCreatedClaim() {
        Claim claim = newClaim(CLAIM_ID);
        Instant createdAt = claim.getCreatedAt();
        Instant updatedAt = claim.getUpdatedAt();
        List<DomainEvent> capturedEvents = claim.getDomainEvents();

        claim.clearEvents();

        assertCreatedBusinessData(claim, CLAIM_ID);
        assertAll(
                () -> assertEquals(createdAt, claim.getCreatedAt()),
                () -> assertEquals(updatedAt, claim.getUpdatedAt()),
                () -> assertEquals(List.of(), claim.getDomainEvents()),
                () -> assertEquals(1, capturedEvents.size(), "Previously captured event snapshots stay intact")
        );
    }

    @Test
    void callersCannotModifyTheSubmissionEventSnapshot() {
        Claim claim = newClaim(CLAIM_ID);
        List<DomainEvent> events = claim.getDomainEvents();

        assertThrows(UnsupportedOperationException.class, events::clear);

        assertEquals(events, claim.getDomainEvents());
        assertEquals(1, claim.getDomainEvents().size());
        assertCreatedBusinessData(claim, CLAIM_ID);
    }

    @Test
    void independentlyCreatedClaimsKeepSeparateEventQueues() {
        Claim first = newClaim(CLAIM_ID);
        Claim second = newClaim(OTHER_CLAIM_ID);

        first.clearEvents();

        assertEquals(List.of(), first.getDomainEvents());
        assertEquals(1, second.getDomainEvents().size());
        ClaimSubmitted secondEvent = assertInstanceOf(ClaimSubmitted.class, second.getDomainEvents().getFirst());
        assertEquals(OTHER_CLAIM_ID, secondEvent.claimId());
        assertCreatedBusinessData(second, OTHER_CLAIM_ID);
    }

    private static Claim newClaim(UUID claimId) {
        return new Claim(claimId, OWNER_ID, INCIDENT_DATE, " \t" + LOCATION + "\r\n ",
                "\n " + DESCRIPTION + "\t ", AMOUNT);
    }

    private static void assertCreatedBusinessData(Claim claim, UUID expectedId) {
        assertAll(
                () -> assertEquals(expectedId, claim.getClaimId()),
                () -> assertEquals(OWNER_ID, claim.getUserId()),
                () -> assertEquals(INCIDENT_DATE, claim.getIncidentDate()),
                () -> assertEquals(LOCATION, claim.getIncidentLocation()),
                () -> assertEquals(DESCRIPTION, claim.getDescription()),
                () -> assertEquals(AMOUNT, claim.getClaimAmount()),
                () -> assertEquals(ClaimStatus.SUBMITTED, claim.getStatus())
        );
    }

    private static void assertWithinOperation(Instant actual, Instant before, Instant after) {
        assertNotNull(actual);
        assertAll(
                () -> assertFalse(actual.isBefore(before)),
                () -> assertFalse(actual.isAfter(after))
        );
    }
}
