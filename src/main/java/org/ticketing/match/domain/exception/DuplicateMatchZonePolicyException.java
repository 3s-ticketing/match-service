package org.ticketing.match.domain.exception;

import java.util.UUID;
import org.ticketing.common.exception.ConflictException;

public class DuplicateMatchZonePolicyException extends ConflictException {

    public DuplicateMatchZonePolicyException(UUID matchId, UUID seatGradeId) {
        super(String.format(
                "ZonePolicy for seatGradeId [%s] already exists in match [%s].",
                seatGradeId, matchId));
    }

    /** DB 제약 위반으로 인한 번역 시 사용 (matchId/seatGradeId 를 특정할 수 없을 때) */
    public DuplicateMatchZonePolicyException() {
        super("Duplicate ZonePolicy detected. A policy for the same seatGrade already exists.");
    }
}
