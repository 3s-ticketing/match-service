package org.ticketing.match.application.service;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;

/**
 * 구역별 잔여 좌석 수를 Caffeine {@link LoadingCache} 로 캐싱하는 서비스.
 *
 * <h3>expireAfterWrite → refreshAfterWrite 전환 이유</h3>
 * <pre>
 *   expireAfterWrite(2s)                     [이전]
 *     TTL 만료 → 엔트리 삭제
 *     다음 요청 → MISS → 1개 스레드 Redis 조회, 나머지 블로킹 대기
 *     → 대기 스레드가 p90/p95 tail latency 로 누적
 *
 *   refreshAfterWrite(2s)                    [현재]
 *     TTL 만료 → 엔트리 유지 (stale)
 *     다음 요청 → 즉시 stale 값 반환 + 백그라운드에서 Redis 조회 트리거
 *     → 사용자는 항상 L1 에서 즉시 반환 (~0ms), tail latency 제거
 * </pre>
 *
 * <h3>Spring {@code @Cacheable} 대신 {@link LoadingCache} 직접 사용</h3>
 * <p>{@code refreshAfterWrite} 는 Caffeine 의 {@link CacheLoader} 가 있는 {@link LoadingCache}
 * 에서만 동작한다. Spring Cache 추상화는 이 조합을 지원하지 않으므로
 * {@link LoadingCache} 를 생성자에서 직접 빌드한다.
 *
 * <h3>최초 로드 (cold miss)</h3>
 * <p>캐시에 엔트리가 없는 최초 요청은 {@link LoadingCache#get} 이 동기적으로 Redis 를 조회한다.
 * 이후 TTL 만료 시 갱신은 {@code ForkJoinPool.commonPool()} 에서 비동기로 처리된다.
 *
 * <h3>백그라운드 갱신 실패</h3>
 * <p>갱신 중 예외 발생 시 Caffeine 은 stale 값을 유지하고 다음 주기에 재시도한다.
 * 잔여 좌석 표시용이므로 일시적 stale 은 허용 범위 내에 있다.
 *
 * <h3>Redis 원본과의 관계</h3>
 * <p>{@code seat:remaining:{matchId}} Redis Hash 는 reservation-service 의 DECR/INCR 이
 * 원자적으로 갱신하는 실시간 원본이다. 이 캐시는 표시용 읽기 전용이며,
 * 실제 예약 가능 여부는 reservation-service 의 Redis DECR 이 단독으로 판단한다.
 */
@Service
public class SeatAvailabilityCacheService {

    private final LoadingCache<UUID, Map<UUID, Long>> cache;

    /**
     * 생성자에서 {@link LoadingCache} 를 직접 빌드한다.
     *
     * <p>{@code refreshAfterWrite(2s)}: TTL 만료 후 첫 요청 시 stale 값을 즉시 반환하고
     * {@code seatAvailabilityRepository::findAll} 을 백그라운드에서 비동기 실행한다.
     *
     * @param seatAvailabilityRepository Redis {@code HGETALL} 을 수행하는 레포지토리.
     *                                   메서드 레퍼런스로 {@link CacheLoader} 에 직접 연결.
     */
    public SeatAvailabilityCacheService(SeatAvailabilityRepository seatAvailabilityRepository) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .refreshAfterWrite(Duration.ofSeconds(2))
                .recordStats()
                .build(seatAvailabilityRepository::findAll);
    }

    /**
     * 경기의 구역별 잔여 좌석 수 조회.
     *
     * <p>캐시 히트(fresh / stale): JVM 메모리에서 즉시 반환 (~0ms).
     * 캐시 미스(최초 요청): Redis {@code HGETALL seat:remaining:{matchId}} 동기 조회.
     * TTL 만료 후: stale 즉시 반환 + 백그라운드 갱신.
     *
     * @param matchId 경기 ID
     * @return seatGradeId → 잔여 좌석 수 맵
     */
    public Map<UUID, Long> getRemaining(UUID matchId) {
        return cache.get(matchId);
    }
}
