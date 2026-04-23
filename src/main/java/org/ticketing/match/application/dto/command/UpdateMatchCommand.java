package org.ticketing.match.application.dto.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateMatchCommand(
        UUID matchId,
        String name,
        OffsetDateTime matchDatetime,
        OffsetDateTime ticketOpenAt
) {
}
