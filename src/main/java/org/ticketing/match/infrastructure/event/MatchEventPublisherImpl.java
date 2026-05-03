package org.ticketing.match.infrastructure.event;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.ticketing.common.event.Events;
import org.ticketing.match.domain.event.MatchEventPublisher;
import org.ticketing.match.domain.event.payload.MatchApprovedEvent;
import org.ticketing.match.domain.event.payload.MatchCanceledEvent;

/**
 * MatchEventPublisher 구현체.
 *
 * <p>common-module 의 {@link Events#trigger} 를 통해 OutboxEvent 를 발행한다.
 * OutboxEventListener 가 동일 트랜잭션에서 Outbox 레코드를 저장하고,
 * AFTER_COMMIT 시점에 Kafka 로 전송한다.
 *
 * <ul>
 *   <li>correlationId : 이벤트마다 고유한 UUID — 중복 발행 방지</li>
 *   <li>domainType    : "MATCH" 고정</li>
 *   <li>domainId      : matchId — Kafka 파티션 키로 사용되어 같은 경기 이벤트는 순서 보장</li>
 *   <li>eventType     : Kafka 토픽명</li>
 * </ul>
 */
@Component
public class MatchEventPublisherImpl implements MatchEventPublisher {

    private static final String DOMAIN_TYPE = "MATCH";

    @Value("${topics.match.approved:match.approved}")
    private String matchApprovedTopic;

    @Value("${topics.match.canceled:match.canceled}")
    private String matchCanceledTopic;

    @Override
    public void publishMatchApproved(MatchApprovedEvent event) {
        Events.trigger(
                UUID.randomUUID().toString(),
                DOMAIN_TYPE,
                event.matchId().toString(),
                matchApprovedTopic,
                event
        );
    }

    @Override
    public void publishMatchCanceled(MatchCanceledEvent event) {
        Events.trigger(
                UUID.randomUUID().toString(),
                DOMAIN_TYPE,
                event.matchId().toString(),
                matchCanceledTopic,
                event
        );
    }
}
