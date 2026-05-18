package org.ticketing.match.application.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;

import static org.ticketing.match.infrastructure.config.MatchCacheConfig.SEAT_REMAINING_CACHE;

/**
 * 구역별 잔여 좌석 수를 Caffeine L1 으로 단기 캐싱하는 서비스.
 *
 * <h3>설계 배경</h3>
 * <p>{@code seat:remaining:{matchId}} Redis Hash 는 예약/취소 시 원자적으로 DECR/INCR 되는
 * 실시간 데이터의 원본이다. 이 값에 L2(Redis) 캐시를 추가하면 원본과 캐시가 이중으로
 * Redis 에 존재하게 되므로 의미가 없다.
 *
 * <p>대신 Caffeine L1 캐시(TTL 2초)만 적용하여 다음 목적을 달성한다:
 * <ul>
 *   <li>티켓 오픈 순간 1,000+ req/s 가 동일 matchId 에 집중될 때
 *       Redis {@code HGETALL} 호출을 <b>2초당 1회</b>로 줄임 (Thundering Herd 완화)</li>
 *   <li>사용자 화면에 보여주는 잔여 좌석 표시는 2초 오차가 허용됨</li>
 *   <li>실제 예약 가능 여부 판단 및 좌석 차감은 reservation-service 의 Redis DECR 으로
 *       별도 처리되므로 정합성에 영향 없음</li>
 * </ul>
 *
 * <h3>Thundering Herd 방지</h3>
 * <p>{@code sync = true}: 2초 TTL 만료 시 다수의 스레드가 동시에 캐시 미스를 내더라도
 * 단 1개 스레드만 {@code HGETALL} 을 호출하고 나머지는 결과를 대기한다.
 * Caffeine 의 {@code cache.get(key, mappingFunction)} 이 JVM 내에서 이를 보장한다.
 *
 * <h3>cacheManager 명시</h3>
 * <p>{@code cacheManager = "caffeineCacheManager"} 를 명시하여 기본 {@code CompositeCacheManager}
 * 를 우회한다. CompositeCacheManager 를 통하면 결과가 Redis L2 에도 기록되어
 * "Redis 가 원본이면서 동시에 캐시" 라는 이중 역할이 생기는 문제를 방지한다.
 */
@Service
@RequiredArgsConstructor
public class SeatAvailabilityCacheService {

    private final SeatAvailabilityRepository seatAvailabilityRepository;

    /**
     * 경기의 구역별 잔여 좌석 수 조회 (Caffeine L1 캐시, TTL 2초).
     *
     * <p>캐시 히트 시 Redis 조회 없이 JVM 메모리에서 즉시 반환.
     * 캐시 미스(2초마다 1회) 시 {@code HGETALL seat:remaining:{matchId}} 를 Redis 에서 조회.
     *
     * @param matchId 경기 ID
     * @return seatGradeId → 잔여 좌석 수 맵
     */
    @Cacheable(
            value = SEAT_REMAINING_CACHE,
            key = "#matchId",
            sync = true,
            cacheManager = "caffeineCacheManager"
    )
    public Map<UUID, Long> getRemaining(UUID matchId) {
        return seatAvailabilityRepository.findAll(matchId);
    }
}
