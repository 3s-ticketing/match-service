package org.ticketing.match.domain.exception;

import org.ticketing.common.exception.BadRequestException;
import org.ticketing.match.domain.model.MatchStatus;

public class MatchNotEditableException extends BadRequestException {

    public MatchNotEditableException(MatchStatus status) {
        super(String.format("Match cannot be edited in status [%s]. Only DRAFT or PENDING_APPROVAL are allowed.", status));
    }
}
