package org.ticketing.match.application.dto.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateMatchCommand(
        UUID homeClubId,
        UUID awayClubId,
        UUID stadiumId,
        String name,
        OffsetDateTime matchDatetime,
        OffsetDateTime ticketOpenAt
) {
}
