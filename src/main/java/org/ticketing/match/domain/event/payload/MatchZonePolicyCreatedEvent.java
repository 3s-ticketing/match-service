package org.ticketing.match.domain.event.payload;

import java.util.UUID;

public record MatchZonePolicyCreatedEvent(
        UUID policyId,
        UUID matchId,
        UUID seatGradeId,
        Long price,
        Boolean isOpen
) {
}
