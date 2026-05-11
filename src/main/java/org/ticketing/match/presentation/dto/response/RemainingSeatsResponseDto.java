package org.ticketing.match.presentation.dto.response;

import java.util.List;
import java.util.UUID;
import org.ticketing.match.application.dto.result.RemainingSeatsResult;
import org.ticketing.match.application.dto.result.RemainingSeatsResult.ZoneAvailability;

public record RemainingSeatsResponseDto(
        UUID matchId,
        List<ZoneDto> zones
) {

    public record ZoneDto(
            UUID seatGradeId,
            Long price,
            Long totalSeatCount,
            Long remainingCount
    ) {

        public static ZoneDto from(ZoneAvailability zone) {
            return new ZoneDto(
                    zone.seatGradeId(),
                    zone.price(),
                    zone.totalSeatCount(),
                    zone.remainingCount()
            );
        }
    }

    public static RemainingSeatsResponseDto from(RemainingSeatsResult result) {
        List<ZoneDto> zones = result.zones().stream()
                .map(ZoneDto::from)
                .toList();
        return new RemainingSeatsResponseDto(result.matchId(), zones);
    }
}
