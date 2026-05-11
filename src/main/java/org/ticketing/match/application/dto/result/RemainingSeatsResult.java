package org.ticketing.match.application.dto.result;

import java.util.List;
import java.util.UUID;

/**
 * 경기의 구역별 실시간 잔여 좌석 수 조회 결과.
 */
public record RemainingSeatsResult(
        UUID matchId,
        List<ZoneAvailability> zones
) {

    public record ZoneAvailability(
            UUID seatGradeId,
            Long price,
            Long totalSeatCount,
            Long remainingCount
    ) {}
}
