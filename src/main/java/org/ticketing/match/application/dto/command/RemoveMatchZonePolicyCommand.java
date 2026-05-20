package org.ticketing.match.application.dto.command;

import java.util.UUID;

public record RemoveMatchZonePolicyCommand(
        UUID matchId,
        UUID policyId,
        String deletedBy
) {
}
