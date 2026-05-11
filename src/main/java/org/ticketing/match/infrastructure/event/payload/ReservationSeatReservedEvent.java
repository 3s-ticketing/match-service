package org.ticketing.match.infrastructure.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * reservation-service 가 발행하는 좌석 예매 완료 이벤트 페이로드.
 * match-service 에서 소비하여 Redis 잔여 좌석 수를 감소시킨다.
 */
public record ReservationSeatReservedEvent(
        UUID reservationSeatId,
        UUID reservationId,
        UUID matchId,
        UUID seatId,
        UUID seatGradeId,
        OffsetDateTime reservedAt
) {}
