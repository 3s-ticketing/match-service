package org.ticketing.match.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationSeatEventConsumer {

    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${topics.reservation.seat.reserved:reservation.seat.reserved}",
            groupId = "${spring.kafka.consumer.group-id:match-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSeatReserved(ConsumerRecord<String, String> record) throws JsonProcessingException {
        ReservationSeatReservedEvent event =
                objectMapper.readValue(record.value(), ReservationSeatReservedEvent.class);
        log.debug("[SeatEvent] reserved matchId={}, seatGradeId={}", event.matchId(), event.seatGradeId());
        seatAvailabilityRepository.decrement(event.matchId(), event.seatGradeId());
    }

    @KafkaListener(
            topics = "${topics.reservation.seat.released:reservation.seat.released}",
            groupId = "${spring.kafka.consumer.group-id:match-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSeatReleased(ConsumerRecord<String, String> record) throws JsonProcessingException {
        ReservationSeatReleasedEvent event =
                objectMapper.readValue(record.value(), ReservationSeatReleasedEvent.class);
        log.debug("[SeatEvent] released matchId={}, seatGradeId={}, reason={}",
                event.matchId(), event.seatGradeId(), event.reason());
        seatAvailabilityRepository.increment(event.matchId(), event.seatGradeId());
    }
}
