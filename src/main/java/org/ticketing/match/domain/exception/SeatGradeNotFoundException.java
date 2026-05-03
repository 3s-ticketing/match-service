package org.ticketing.match.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.NotFoundException;

public class SeatGradeNotFoundException extends NotFoundException {

    public SeatGradeNotFoundException(UUID seatGradeId) {
        super("SeatGrade not found: " + seatGradeId);
    }
}
