package org.ticketing.match.domain.event.payload;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 경기 취소 이벤트.
 * OPEN 상태에서 취소될 경우 예매·결제 서비스가 이 이벤트를 소비하여
 * 진행 중인 예매 취소 및 환불 처리를 수행한다.
 */
public record MatchCanceledEvent(
        UUID matchId,
        OffsetDateTime canceledAt
) {
}
