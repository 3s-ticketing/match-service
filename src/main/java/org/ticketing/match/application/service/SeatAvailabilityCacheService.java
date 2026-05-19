package org.ticketing.match.application.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;
import org.ticketing.match.infrastructure.config.MatchCacheConfig;

/**
 * 구역별 잔여 좌석 수를 Caffeine L1 캐시로 캐싱하는 서비스.
 *
 * <h3>캐시 전략</h3>
 * <pre>
 *   expireAfterWrite(2s) + @Cacheable(sync=true)
 *     TTL 만료 → 엔트리 삭제
 *     다음 요청 → MISS → 1개 스레드만 Redis 조회, 나머지는 lock 대기 (stampede 방지)
 *     조회 완료 → 2초간 L1 에서 즉시 반환
 * </pre>
 *
 * <h3>Redis 원본과의 관계</h3>
 * <p>{@code seat:remaining:{matchId}} Redis Hash 는 reservation-service 의 DECR/INCR 이
 * 원자적으로 갱신하는 실시간 원본이다. 이 캐시는 표시용 읽기 전용이며,
 * 실제 예약 가능 여부는 reservation-service 의 Redis DECR 이 단독으로 판단한다.
 */
@Service
@RequiredArgsConstructor
public class SeatAvailabilityCacheService {

    private final SeatAvailabilityRepository seatAvailabilityRepository;

    /**
     * 경기의 구역별 잔여 좌석 수 조회.
     *
     * <p>캐시 히트: Caffeine L1 에서 즉시 반환 (~0ms).
     * 캐시 미스: Redis {@code HGETALL seat:remaining:{matchId}} 동기 조회.
     * {@code sync=true} 로 단일 로더만 Redis 를 조회하고 나머지는 대기(stampede 방지).
     *
     * @param matchId 경기 ID
     * @return seatGradeId → 잔여 좌석 수 맵
     */
    @Cacheable(value = MatchCacheConfig.SEAT_REMAINING_CACHE, key = "#matchId", sync = true)
    public Map<UUID, Long> getRemaining(UUID matchId) {
        return seatAvailabilityRepository.findAll(matchId);
    }
}
