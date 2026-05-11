package org.ticketing.match.infrastructure.redis;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;

/**
 * Redis Hash 기반 SeatAvailabilityRepository 구현체.
 *
 * <p>Hash 키 구조: {@code seat:remaining:{matchId}}
 * <p>field: seatGradeId(UUID 문자열), value: 잔여 좌석 수(Long 문자열)
 *
 * <h3>음수 방지</h3>
 * <p>{@link #decrement} 는 Lua 스크립트를 사용하여 원자적으로 0 이하 감소를 막는다.
 * 단순 {@code HINCRBY -1} 은 중복 이벤트 / 재시도 시 음수가 될 수 있으며,
 * 이는 비즈니스 불변식(잔여 좌석 ≥ 0) 위반이다.
 *
 * <h3>Lua 스크립트 반환값</h3>
 * <ul>
 *   <li>-1: 키 또는 필드 없음 (Redis 초기화 전 이벤트 수신)</li>
 *   <li>0: 이미 0 이하 — 감소 무시</li>
 *   <li>양수: 감소 후 잔여 좌석 수</li>
 * </ul>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisSeatAvailabilityRepository implements SeatAvailabilityRepository {

    private static final String KEY_PREFIX = "seat:remaining:";

    /**
     * 잔여 좌석이 1 이상일 때만 원자적으로 감소.
     * HGET → 값이 없으면 -1 반환, 0 이하면 현재값 반환, 양수면 HINCRBY -1 후 결과 반환.
     */
    private static final RedisScript<Long> DECREMENT_IF_POSITIVE = RedisScript.of(
            "local v = redis.call('HGET', KEYS[1], ARGV[1])\n"
                    + "if v == false then return -1 end\n"
                    + "local n = tonumber(v)\n"
                    + "if n <= 0 then return 0 end\n"
                    + "return redis.call('HINCRBY', KEYS[1], ARGV[1], -1)",
            Long.class
    );

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
        Long result = redisTemplate.execute(
                DECREMENT_IF_POSITIVE,
                List.of(key),
                seatGradeId.toString()
        );
        if (result == null || result < 0) {
            log.warn("[SeatAvailability] decrement 실패 — Redis 키 없음. "
                    + "APPROVED 전 이벤트 수신 또는 키 만료. matchId={}, seatGradeId={}", matchId, seatGradeId);
        } else if (result == 0) {
            log.warn("[SeatAvailability] decrement 무시 — 잔여 좌석 이미 0. "
                    + "중복 이벤트 또는 초과 예매 시도. matchId={}, seatGradeId={}", matchId, seatGradeId);
        } else {
            log.debug("[SeatAvailability] decrement matchId={}, seatGradeId={}, remaining={}", matchId, seatGradeId, result);
        }
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
