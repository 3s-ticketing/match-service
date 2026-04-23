package org.ticketing.match.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class MatchZonePolicyNotFoundException extends NotFoundException {

    public MatchZonePolicyNotFoundException(UUID policyId) {
        super("MatchZonePolicy not found: " + policyId);
    }
}
