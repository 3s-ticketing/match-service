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
 * <p>{@code CompositeCacheManager.getCache()} 는 첫 번째 매니저(Caffeine)의 Cache 만 반환하므로,
 * {@code @CacheEvict} 어노테이션만으로는 L2(Redis) 가 삭제되지 않는다.
 * {@link #evict} 는 두 캐시 매니저에 각각 직접 evict 를 호출한다.
 */
@Slf4j
@Service
public class MatchSnapshotCacheService {

    private final MatchRepository matchRepository;
    private final CacheManager caffeineCacheManager;
    private final CacheManager redisCacheManager;

    public MatchSnapshotCacheService(
            MatchRepository matchRepository,
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            @Qualifier("redisCacheManager") CacheManager redisCacheManager
    ) {
        this.matchRepository = matchRepository;
        this.caffeineCacheManager = caffeineCacheManager;
        this.redisCacheManager = redisCacheManager;
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
     * L1(Caffeine) + L2(Redis) 양쪽 캐시 무효화.
     *
     * <p>{@code CompositeCacheManager} 는 {@code getCache()} 시 첫 번째 매니저(Caffeine) 만 반환하므로,
     * {@code @CacheEvict} 단독으로는 Redis L2 가 삭제되지 않는다.
     * 이 메서드는 두 캐시 매니저에 각각 직접 evict 를 호출하여 양쪽을 모두 무효화한다.
     *
     * <p>Redis 장애 시 {@link org.ticketing.match.infrastructure.config.MatchCacheConfig}
     * 의 {@code CacheErrorHandler} 가 Redis evict 오류를 로깅 후 무시한다.
     * L1(Caffeine) 은 항상 삭제되므로 TTL(5분) 이내에 최신 데이터가 L1 에서 제공된다.
     */
    public void evict(UUID matchId) {
        evictFromManager(caffeineCacheManager, matchId, "L1-Caffeine");
        evictFromManager(redisCacheManager, matchId, "L2-Redis");
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
