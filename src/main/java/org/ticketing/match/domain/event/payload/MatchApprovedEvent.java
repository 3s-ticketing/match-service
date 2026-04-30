package org.ticketing.match.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 경기 승인(APPROVED) 이벤트.
 *
 * <p>reservation-service 가 이 이벤트를 소비하여 ticketOpenAt 캐시를 초기화한다.
 * 캐시는 RESERVED Redis 락의 TTL 산정에 사용된다.
 */
public record MatchApprovedEvent(
        UUID matchId,
        OffsetDateTime ticketOpenAt
) {
}
