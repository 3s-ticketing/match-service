package org.ticketing.match.application.dto.command;

import java.util.UUID;

public record DeleteMatchCommand(
        UUID matchId,
        String deletedBy
) {
}
