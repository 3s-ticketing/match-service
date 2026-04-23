package org.ticketing.match.application.dto.command;

import java.util.UUID;

public record AddMatchZonePolicyCommand(
        UUID matchId,
        UUID seatGradeId,
        Long price
) {
}
