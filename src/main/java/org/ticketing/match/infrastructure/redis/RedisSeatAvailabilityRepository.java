package org.ticketing.match.infrastructure.redis;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;

/**
 * Redis Hash 기반 SeatAvailabilityRepository 구현체.
 *
 * <p>Hash 키 구조: {@code seat:remaining:{matchId}}
 * <p>field: seatGradeId(UUID 문자열), value: 잔여 좌석 수(Long 문자열)
 *
 * <p>{@code HINCRBY} 를 사용하므로 decrement / increment 가 원자적으로 처리된다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisSeatAvailabilityRepository implements SeatAvailabilityRepository {

    private static final String KEY_PREFIX = "seat:remaining:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void initialize(UUID matchId, Map<UUID, Long> seatCountByGrade) {
        String key = buildKey(matchId);
        Map<String, String> hash = new HashMap<>();
        seatCountByGrade.forEach((gradeId, count) -> hash.put(gradeId.toString(), count.toString()));
        redisTemplate.opsForHash().putAll(key, hash);
        log.info("[SeatAvailability] initialized matchId={}, grades={}", matchId, seatCountByGrade.size());
    }

    @Override
    public void decrement(UUID matchId, UUID seatGradeId) {
        String key = buildKey(matchId);
        Long remaining = redisTemplate.opsForHash().increment(key, seatGradeId.toString(), -1L);
        log.debug("[SeatAvailability] decrement matchId={}, seatGradeId={}, remaining={}", matchId, seatGradeId, remaining);
    }

    @Override
    public void increment(UUID matchId, UUID seatGradeId) {
        String key = buildKey(matchId);
        Long remaining = redisTemplate.opsForHash().increment(key, seatGradeId.toString(), 1L);
        log.debug("[SeatAvailability] increment matchId={}, seatGradeId={}, remaining={}", matchId, seatGradeId, remaining);
    }

    @Override
    public Map<UUID, Long> findAll(UUID matchId) {
        String key = buildKey(matchId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Long> result = new HashMap<>();
        entries.forEach((field, value) -> {
            try {
                UUID gradeId = UUID.fromString(field.toString());
                long count = Long.parseLong(value.toString());
                result.put(gradeId, count);
            } catch (Exception e) {
                log.warn("[SeatAvailability] invalid entry key={}, field={}", key, field);
            }
        });
        return result;
    }

    private String buildKey(UUID matchId) {
        return KEY_PREFIX + matchId;
    }
}
