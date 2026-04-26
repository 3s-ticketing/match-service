package org.ticketing.match.application.dto.command;

import java.util.UUID;

public record UpdateMatchZonePolicyCommand(
        UUID matchId,
        UUID policyId,
        Long price
) {
}
