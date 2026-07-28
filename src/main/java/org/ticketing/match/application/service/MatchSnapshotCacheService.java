package org.ticketing.match.application.service;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.match.application.dto.result.MatchSnapshot;
import org.ticketing.match.domain.exception.MatchNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.repository.MatchRepository;
import org.ticketing.match.infrastructure.config.MatchCacheInvalidationPublisher;

import static org.ticketing.match.infrastructure.config.MatchCacheConfig.MATCH_SNAPSHOT_CACHE;

/**
 * Match + ZonePolicy 데이터를 L1(Caffeine) → L2(Redis) 2계층으로 캐싱하는 서비스.
 *
 * <h3>캐시 전략</h3>
 * <ul>
 *   <li>캐시 히트(L1) : Caffeine JVM 로컬 캐시에서 즉시 반환 (~0ms, DB·Redis 쿼리 0회)</li>
 *   <li>캐시 히트(L2) : Redis 에서 {@link MatchSnapshot} 반환 (~1ms, DB 쿼리 0회)</li>
 *   <li>캐시 미스 : fetch join 으로 Match + ZonePolicies 를 단 1번 DB 조회 후 L1·L2 모두 저장</li>
 *   <li>쓰기(update/delete/addZonePolicy 등) 후 {@link #evict} 호출로 L1·L2 모두 무효화</li>
 * </ul>
 *
 * <h3>Cache Stampede 방지</h3>
 * <p>{@code sync = true}: 캐시 미스 시 단 하나의 스레드만 DB 를 조회하고 나머지는 결과를 기다린다.
 * Caffeine 은 {@code computeIfAbsent} 로 JVM 내 단일 로더를 보장한다.
 *
 * <h3>Self-invocation 주의</h3>
 * <p>{@code @Cacheable} 은 Spring AOP 프록시를 통해서만 동작한다.
 * 같은 빈 내에서 {@code this.getSnapshot()} 을 호출하면 프록시를 우회하여 캐시가 적용되지 않는다.
 * Warm-up 은 반드시 외부 빈({@link org.ticketing.match.application.service.MatchWriteService})에서
 * 이 서비스의 프록시 메서드({@link #getSnapshot})를 직접 호출해야 한다.
 *
 * <h3>Evict 양쪽 계층 무효화</h3>
 * <p>Primary {@link CacheManager} 는 {@link TwoLevelCache} 를 통해 L1·L2 를 함께 관리하지만,
 * {@link #evict} 는 각 매니저에 직접 evict 를 호출하여 명시적으로 양쪽을 무효화한다.
 * 이를 통해 Primary 빈 교체 등의 구성 변경이 있어도 evict 동작이 보장된다.
 */
@Slf4j
@Service
public class MatchSnapshotCacheService {

    private final MatchRepository matchRepository;
    private final CacheManager caffeineCacheManager;
    private final CacheManager redisCacheManager;
    private final MatchCacheInvalidationPublisher invalidationPublisher;

    public MatchSnapshotCacheService(
            MatchRepository matchRepository,
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            @Qualifier("redisCacheManager") CacheManager redisCacheManager,
            MatchCacheInvalidationPublisher invalidationPublisher
    ) {
        this.matchRepository = matchRepository;
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisCacheManager = redisCacheManager;
        this.invalidationPublisher = invalidationPublisher;
    }

    /**
     * Match 스냅샷 조회 (캐시 우선, L1 → L2 → DB).
     *
     * <p>{@code sync = true}: 캐시 미스 시 단 하나의 스레드만 DB 를 조회하고
     * 나머지는 결과를 기다린다 — Cache Stampede 방지.
     *
     * <p>{@code @Transactional(readOnly = true)}: 캐시 미스 시 DB 조회에만 적용.
     * 캐시 히트 시에는 트랜잭션이 열리지 않으므로 DB 커넥션을 소비하지 않는다.
     */
    @Cacheable(value = MATCH_SNAPSHOT_CACHE, key = "#matchId", sync = true)
    @Transactional(readOnly = true)
    public MatchSnapshot getSnapshot(UUID matchId) {
        Match match = matchRepository.findActiveByIdWithPolicies(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        return MatchSnapshot.from(match);
    }

    /**
     * L1(Caffeine) + L2(Redis) 양쪽 캐시 무효화 + 다른 인스턴스로 무효화 전파.
     *
     * <p>각 캐시 매니저에 직접 evict 를 호출하여 L1·L2 를 명시적으로 무효화한다.
     * Primary {@link org.springframework.cache.CacheManager} 가
     * {@link org.ticketing.match.infrastructure.config.TwoLevelCache} 를 사용하더라도
     * 내부 Cache 인스턴스는 공유되므로 직접 evict 와 동일한 효과를 낸다.
     *
     * <p>Redis 장애 시 {@link org.ticketing.match.infrastructure.config.MatchCacheConfig}
     * 의 {@code CacheErrorHandler} 가 Redis evict 오류를 로깅 후 무시한다.
     * L1(Caffeine) 은 항상 삭제되므로 TTL(5분) 이내에 최신 데이터가 L1 에서 제공된다.
     *
     * <p><b>크로스 인스턴스 전파:</b> match-service 는 고가용성을 위해 여러 인스턴스로 수평 복제되며,
     * L1(Caffeine) 은 인스턴스마다 격리된 JVM 로컬 캐시다. 자신의 L1·L2 를 evict 한 뒤
     * {@link MatchCacheInvalidationPublisher} 로 matchId 를 Redis 채널에 발행해, 다른 인스턴스의
     * 리스너가 각자의 L1 도 함께 evict 하도록 한다. 발행이 실패해도 L1 TTL(5분)이 안전망 역할을 한다.
     */
    public void evict(UUID matchId) {
        evictFromManager(caffeineCacheManager, matchId, "L1-Caffeine");
        evictFromManager(redisCacheManager, matchId, "L2-Redis");
        invalidationPublisher.publishEvict(matchId);
    }

    private void evictFromManager(CacheManager manager, UUID matchId, String label) {
        Cache cache = manager.getCache(MATCH_SNAPSHOT_CACHE);
        if (cache != null) {
            cache.evict(matchId);
            log.debug("[Cache] {} evict — matchId={}", label, matchId);
        } else {
            log.warn("[Cache] {} getCache({}) 반환 null — evict 생략. matchId={}",
                    label, MATCH_SNAPSHOT_CACHE, matchId);
        }
    }
}
