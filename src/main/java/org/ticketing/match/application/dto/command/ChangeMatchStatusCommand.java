package org.ticketing.match.application.dto.command;

import java.util.UUID;
import org.ticketing.match.domain.model.MatchStatus;

public record ChangeMatchStatusCommand(
        UUID matchId,
        MatchStatus targetStatus
) {
}
