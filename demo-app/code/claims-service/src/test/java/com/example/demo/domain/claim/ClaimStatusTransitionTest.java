package com.example.demo.domain.claim;

import com.example.demo.domain.DomainEvent;
import com.example.demo.domain.claim.events.ClaimStatusChanged;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
@DisplayName("CM-UT-005: claim status changes and business side effects")
class ClaimStatusTransitionTest {

    private static final UUID CLAIM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate INCIDENT_DATE = LocalDate.of(2024, 1, 1);
    private static final String LOCATION = "12 Example Road";
    private static final String DESCRIPTION = "Water damage occurred near the kitchen sink.";
    private static final BigDecimal AMOUNT = new BigDecimal("1234.56");
    private static final Instant CREATED_AT = Instant.parse("2024-01-02T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2024-01-03T10:00:00Z");

    @ParameterizedTest(name = "{0} -> {1} updates the claim and emits one status event")
    @CsvSource({
        "SUBMITTED, UNDER_REVIEW",
        "SUBMITTED, REJECTED",
        "UNDER_REVIEW, APPROVED",
        "UNDER_REVIEW, REJECTED",
        "UNDER_REVIEW, SUBMITTED",
        "APPROVED, CLOSED",
        "REJECTED, CLOSED"
    })
    void validTransitionUpdatesStatusAndEmitsOneEvent(ClaimStatus current, ClaimStatus target) {
        Claim claim = claimInStatus(current);
        Instant before = Instant.now();

        claim.updateStatus(target);

        Instant after = Instant.now();
        assertEquals(target, claim.getStatus());
        assertWithinOperation(claim.getUpdatedAt(), before, after);
        assertBusinessFieldsUnchanged(claim);
        assertEquals(1, claim.getDomainEvents().size(), "One decision must produce exactly one event");
        ClaimStatusChanged event = assertInstanceOf(ClaimStatusChanged.class, claim.getDomainEvents().getFirst());
        assertAll(
                () -> assertEquals(CLAIM_ID, event.claimId()),
                () -> assertEquals(current, event.oldStatus()),
                () -> assertEquals(target, event.newStatus()),
                () -> assertNotNull(event.eventId()),
                () -> assertFalse(event.eventId().isBlank()),
                () -> assertWithinOperation(event.occurredAt(), before, after)
        );
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected without changing claim data or events")
    @CsvSource({
        "SUBMITTED, SUBMITTED",
        "SUBMITTED, APPROVED",
        "SUBMITTED, CLOSED",
        "UNDER_REVIEW, UNDER_REVIEW",
        "UNDER_REVIEW, CLOSED",
        "APPROVED, SUBMITTED",
        "APPROVED, UNDER_REVIEW",
        "APPROVED, APPROVED",
        "APPROVED, REJECTED",
        "REJECTED, SUBMITTED",
        "REJECTED, UNDER_REVIEW",
        "REJECTED, APPROVED",
        "REJECTED, REJECTED",
        "CLOSED, SUBMITTED",
        "CLOSED, UNDER_REVIEW",
        "CLOSED, APPROVED",
        "CLOSED, REJECTED",
        "CLOSED, CLOSED"
    })
    void invalidTransitionPreservesClaimAndEvents(ClaimStatus current, ClaimStatus target) {
        Claim claim = claimInStatus(current);
        List<DomainEvent> eventsBefore = claim.getDomainEvents();

        InvalidStatusTransitionException error = assertThrows(
                InvalidStatusTransitionException.class, () -> claim.updateStatus(target));

        assertAll(
                () -> assertTrue(error.getMessage().contains(current.name()), "Error must identify the current status"),
                () -> assertTrue(error.getMessage().contains(target.name()), "Error must identify the target status"),
                () -> assertEquals(current, claim.getStatus()),
                () -> assertEquals(UPDATED_AT, claim.getUpdatedAt()),
                () -> assertEquals(eventsBefore, claim.getDomainEvents())
        );
        assertBusinessFieldsUnchanged(claim);
    }

    @ParameterizedTest(name = "Rejecting a repeated {1} decision retains the previous successful {0} -> {1} event")
    @CsvSource({
        "SUBMITTED, UNDER_REVIEW",
        "SUBMITTED, REJECTED",
        "UNDER_REVIEW, APPROVED",
        "UNDER_REVIEW, REJECTED",
        "UNDER_REVIEW, SUBMITTED",
        "APPROVED, CLOSED",
        "REJECTED, CLOSED"
    })
    void rejectedDecisionPreservesPendingSuccessfulEvent(ClaimStatus current, ClaimStatus target) {
        Claim claim = claimInStatus(current);
        claim.updateStatus(target);
        Instant successfulUpdate = claim.getUpdatedAt();
        List<DomainEvent> pendingEvents = claim.getDomainEvents();

        assertThrows(InvalidStatusTransitionException.class, () -> claim.updateStatus(target));

        assertAll(
                () -> assertEquals(target, claim.getStatus()),
                () -> assertEquals(successfulUpdate, claim.getUpdatedAt()),
                () -> assertEquals(pendingEvents, claim.getDomainEvents(),
                        "A rejected decision must neither clear nor append pending events")
        );
        assertBusinessFieldsUnchanged(claim);
    }

    @ParameterizedTest(name = "Submitted claim can be reviewed, {0}, and closed with ordered events")
    @EnumSource(value = ClaimStatus.class, names = {"APPROVED", "REJECTED"})
    void completeLifecycleUsesCurrentStateAndKeepsEventsInOrder(ClaimStatus decision) {
        Claim claim = claimInStatus(ClaimStatus.SUBMITTED);

        claim.updateStatus(ClaimStatus.UNDER_REVIEW);
        claim.updateStatus(decision);
        claim.updateStatus(ClaimStatus.CLOSED);

        assertEquals(ClaimStatus.CLOSED, claim.getStatus());
        List<DomainEvent> events = claim.getDomainEvents();
        assertEquals(3, events.size());
        assertEventTransition(events.get(0), ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);
        assertEventTransition(events.get(1), ClaimStatus.UNDER_REVIEW, decision);
        assertEventTransition(events.get(2), decision, ClaimStatus.CLOSED);
        assertBusinessFieldsUnchanged(claim);
        Instant closedAt = claim.getUpdatedAt();

        assertThrows(InvalidStatusTransitionException.class, () -> claim.updateStatus(ClaimStatus.UNDER_REVIEW));

        assertAll(
                () -> assertEquals(ClaimStatus.CLOSED, claim.getStatus()),
                () -> assertEquals(closedAt, claim.getUpdatedAt()),
                () -> assertEquals(events, claim.getDomainEvents())
        );
    }

    private static Claim claimInStatus(ClaimStatus status) {
        // Reconstitution gives every case a fresh aggregate without a submission event or external dependency.
        return Claim.reconstitute(CLAIM_ID, OWNER_ID, INCIDENT_DATE, LOCATION, DESCRIPTION, AMOUNT,
                status, CREATED_AT, UPDATED_AT);
    }

    private static void assertBusinessFieldsUnchanged(Claim claim) {
        assertAll(
                () -> assertEquals(CLAIM_ID, claim.getClaimId()),
                () -> assertEquals(OWNER_ID, claim.getUserId()),
                () -> assertEquals(INCIDENT_DATE, claim.getIncidentDate()),
                () -> assertEquals(LOCATION, claim.getIncidentLocation()),
                () -> assertEquals(DESCRIPTION, claim.getDescription()),
                () -> assertEquals(AMOUNT, claim.getClaimAmount()),
                () -> assertEquals(CREATED_AT, claim.getCreatedAt())
        );
    }

    private static void assertWithinOperation(Instant actual, Instant before, Instant after) {
        assertNotNull(actual);
        assertAll(
                () -> assertFalse(actual.isBefore(before), "Timestamp must not predate the operation"),
                () -> assertFalse(actual.isAfter(after), "Timestamp must not follow the operation")
        );
    }

    private static void assertEventTransition(DomainEvent domainEvent, ClaimStatus oldStatus, ClaimStatus newStatus) {
        ClaimStatusChanged event = assertInstanceOf(ClaimStatusChanged.class, domainEvent);
        assertAll(
                () -> assertEquals(CLAIM_ID, event.claimId()),
                () -> assertEquals(oldStatus, event.oldStatus()),
                () -> assertEquals(newStatus, event.newStatus())
        );
    }
}
