package org.ticketing.match.domain.exception;

import org.ticketing.common.exception.BadRequestException;
import org.ticketing.match.domain.model.MatchStatus;

public class InvalidMatchStatusTransitionException extends BadRequestException {

    public InvalidMatchStatusTransitionException(MatchStatus current, MatchStatus target) {
        super(String.format("Cannot transition match status from [%s] to [%s]", current, target));
    }
}
