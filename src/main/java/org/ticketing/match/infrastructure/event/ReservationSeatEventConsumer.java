package org.ticketing.match.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;
import org.ticketing.match.infrastructure.event.payload.ReservationSeatReleasedEvent;
import org.ticketing.match.infrastructure.event.payload.ReservationSeatReservedEvent;

/**
 * reservation-service 에서 발행하는 좌석 예매/해제 이벤트를 소비하여
 * Redis 잔여 좌석 카운터를 갱신하는 Kafka 컨슈머.
 *
 * <ul>
 *   <li>{@code reservation.seat.reserved} → {@link SeatAvailabilityRepository#decrement}</li>
 *   <li>{@code reservation.seat.released} → {@link SeatAvailabilityRepository#increment}</li>
 * </ul>
 *
 * <h3>오류 처리</h3>
 * <p>예외를 catch 하지 않고 전파하여 {@link KafkaConsumerConfig} 의 {@code DefaultErrorHandler} 가
 * 처리하도록 한다.
 * <ul>
 *   <li>{@link JsonProcessingException}: non-retryable — 즉시 {@code {topic}.DLT} 로 이동</li>
 *   <li>Redis 오류 등 런타임 예외: 1초 간격 3회 재시도 후 DLT 로 이동</li>
 * </ul>
 *
 * <h3>멱등성</h3>
 * <p>Kafka 는 at-least-once 전달을 보장하므로 동일 이벤트가 중복 소비될 수 있다.
 * {@code reservationSeatId} 를 멱등성 키로 사용하여 Redis SETNX 로 중복 처리를 방지한다.
 * <ul>
 *   <li>키: {@code seat:event:reserved:{reservationSeatId}}, {@code seat:event:released:{reservationSeatId}}</li>
 *   <li>TTL: 24시간 — 재시도 윈도우를 충분히 커버</li>
 *   <li>처리 실패 시 키 롤백 → DefaultErrorHandler 재시도 시 재처리 가능</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSeatEventConsumer {

    private static final String IDEMPOTENCY_RESERVED_PREFIX = "seat:event:reserved:";
    private static final String IDEMPOTENCY_RELEASED_PREFIX = "seat:event:released:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    @KafkaListener(
            topics = "${topics.reservation.seat.reserved:reservation.seat.reserved}",
            groupId = "${spring.kafka.consumer.group-id:match-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSeatReserved(ConsumerRecord<String, String> record) throws JsonProcessingException {
        ReservationSeatReservedEvent event =
                objectMapper.readValue(record.value(), ReservationSeatReservedEvent.class);

        String idempotencyKey = IDEMPOTENCY_RESERVED_PREFIX + event.reservationSeatId();
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);
        if (!Boolean.TRUE.equals(isNew)) {
            log.info("[SeatEvent] 중복 이벤트 스킵 (reserved) reservationSeatId={}", event.reservationSeatId());
            return;
        }

        log.debug("[SeatEvent] reserved matchId={}, seatGradeId={}", event.matchId(), event.seatGradeId());
        try {
            seatAvailabilityRepository.decrement(event.matchId(), event.seatGradeId());
        } catch (Exception e) {
            // 처리 실패 시 멱등성 키 롤백 — DefaultErrorHandler 재시도 시 재처리 가능
            redisTemplate.delete(idempotencyKey);
            throw e;
        }
    }

    @KafkaListener(
            topics = "${topics.reservation.seat.released:reservation.seat.released}",
            groupId = "${spring.kafka.consumer.group-id:match-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSeatReleased(ConsumerRecord<String, String> record) throws JsonProcessingException {
        ReservationSeatReleasedEvent event =
                objectMapper.readValue(record.value(), ReservationSeatReleasedEvent.class);

        String idempotencyKey = IDEMPOTENCY_RELEASED_PREFIX + event.reservationSeatId();
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", IDEMPOTENCY_TTL);
        if (!Boolean.TRUE.equals(isNew)) {
            log.info("[SeatEvent] 중복 이벤트 스킵 (released) reservationSeatId={}", event.reservationSeatId());
            return;
        }

        log.debug("[SeatEvent] released matchId={}, seatGradeId={}, reason={}",
                event.matchId(), event.seatGradeId(), event.reason());
        try {
            seatAvailabilityRepository.increment(event.matchId(), event.seatGradeId());
        } catch (Exception e) {
            redisTemplate.delete(idempotencyKey);
            throw e;
        }
    }
}
