package org.ticketing.match.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.ticketing.match.domain.model.MatchStatus;

public record MatchCreatedEvent(
        UUID matchId,
        UUID homeClubId,
        UUID awayClubId,
        UUID stadiumId,
        String name,
        OffsetDateTime matchDatetime,
        OffsetDateTime ticketOpenAt,
        MatchStatus status
) {
}
