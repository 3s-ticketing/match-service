package org.ticketing.match.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class ClubNotFoundException extends NotFoundException {

    public ClubNotFoundException(UUID clubId) {
        super("Club not found: " + clubId);
    }
}
