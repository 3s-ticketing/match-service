package org.ticketing.match.domain.event.payload;

import java.util.UUID;
import org.ticketing.match.domain.model.MatchStatus;

public record MatchStatusChangedEvent(
        UUID matchId,
        MatchStatus previousStatus,
        MatchStatus currentStatus
) {
}
