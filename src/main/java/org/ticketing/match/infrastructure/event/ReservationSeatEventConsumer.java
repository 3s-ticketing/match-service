package org.ticketing.match.infrastructure.event;

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
    public void onSeatReserved(ConsumerRecord<String, String> record) {
        try {
            ReservationSeatReservedEvent event =
                    objectMapper.readValue(record.value(), ReservationSeatReservedEvent.class);
            log.debug("[SeatEvent] reserved matchId={}, seatGradeId={}", event.matchId(), event.seatGradeId());
            seatAvailabilityRepository.decrement(event.matchId(), event.seatGradeId());
        } catch (Exception e) {
            log.error("[SeatEvent] failed to process reserved event: {}", record.value(), e);
        }
    }

    @KafkaListener(
            topics = "${topics.reservation.seat.released:reservation.seat.released}",
            groupId = "${spring.kafka.consumer.group-id:match-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSeatReleased(ConsumerRecord<String, String> record) {
        try {
            ReservationSeatReleasedEvent event =
                    objectMapper.readValue(record.value(), ReservationSeatReleasedEvent.class);
            log.debug("[SeatEvent] released matchId={}, seatGradeId={}, reason={}", event.matchId(), event.seatGradeId(), event.reason());
            seatAvailabilityRepository.increment(event.matchId(), event.seatGradeId());
        } catch (Exception e) {
            log.error("[SeatEvent] failed to process released event: {}", record.value(), e);
        }
    }
}
