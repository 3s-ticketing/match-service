package org.ticketing.match.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class MatchNotFoundException extends NotFoundException {

    public MatchNotFoundException(UUID matchId) {
        super("Match not found: " + matchId);
    }
}
