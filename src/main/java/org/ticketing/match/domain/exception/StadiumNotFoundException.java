package org.ticketing.match.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class StadiumNotFoundException extends NotFoundException {

    public StadiumNotFoundException(UUID stadiumId) {
        super("Stadium not found: " + stadiumId);
    }
}
