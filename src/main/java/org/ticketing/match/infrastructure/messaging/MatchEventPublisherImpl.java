package org.ticketing.match.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.ticketing.match.domain.event.MatchEventPublisher;
import org.ticketing.match.domain.event.payload.MatchCanceledEvent;
import org.ticketing.match.domain.event.payload.MatchCreatedEvent;
import org.ticketing.match.domain.event.payload.MatchStatusChangedEvent;
import org.ticketing.match.domain.event.payload.MatchZonePolicyCreatedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchEventPublisherImpl implements MatchEventPublisher {

    private static final String TOPIC_MATCH_CREATED        = "match.created";
    private static final String TOPIC_MATCH_CANCELED       = "match.canceled";
    private static final String TOPIC_MATCH_STATUS_CHANGED = "match.status-changed";
    private static final String TOPIC_ZONE_POLICY_CREATED  = "match.zone-policy.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishMatchCreated(MatchCreatedEvent event) {
        kafkaTemplate.send(TOPIC_MATCH_CREATED, event.matchId().toString(), event);
        log.info("[MatchEvent] publishMatchCreated: matchId={}", event.matchId());
    }

    /**
     * OPEN 상태에서 취소된 경우 예매·결제 서비스가 이 이벤트를 소비한다.
     * 프로덕션에서는 Outbox 패턴으로 교체해 이벤트 유실을 방지해야 한다.
     */
    @Override
    public void publishMatchCanceled(MatchCanceledEvent event) {
        kafkaTemplate.send(TOPIC_MATCH_CANCELED, event.matchId().toString(), event);
        log.info("[MatchEvent] publishMatchCanceled: matchId={}, canceledAt={}", event.matchId(), event.canceledAt());
    }

    @Override
    public void publishMatchStatusChanged(MatchStatusChangedEvent event) {
        kafkaTemplate.send(TOPIC_MATCH_STATUS_CHANGED, event.matchId().toString(), event);
        log.info("[MatchEvent] publishMatchStatusChanged: matchId={}, {} -> {}",
                event.matchId(), event.previousStatus(), event.currentStatus());
    }

    @Override
    public void publishMatchZonePolicyCreated(MatchZonePolicyCreatedEvent event) {
        kafkaTemplate.send(TOPIC_ZONE_POLICY_CREATED, event.policyId().toString(), event);
        log.info("[MatchEvent] publishMatchZonePolicyCreated: matchId={}, policyId={}", event.matchId(), event.policyId());
    }
}
