package org.ticketing.match.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 경기 승인(APPROVED) 이벤트.
 * queue-service 가 이 이벤트를 소비하여 티켓 오픈 시간 캐시를 초기화한다.
 */
public record MatchApprovedEvent(
        UUID matchId,
        OffsetDateTime ticketOpenAt
) {
}
