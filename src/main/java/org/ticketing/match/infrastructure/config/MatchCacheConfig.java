package org.ticketing.match.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * L1(Caffeine) + L2(Redis) 2계층 캐시 구성.
 *
 * <h3>캐시 목록</h3>
 * <pre>
 *   match-snapshot  : Match + ZonePolicy 정적 구조 데이터
 *                     L1(Caffeine 5분) → L2(Redis 60분) → DB(fetch join)
 *
 *   seat-remaining  : 구역별 잔여 좌석 수 표시용 스냅샷
 *                     L1(Caffeine 2초) 전용 — Redis 는 원본 데이터이므로 L2 캐시 없음
 *                     TTL 2초: Thundering Herd to Redis 완화, 허용 가능한 표시 지연
 * </pre>
 *
 * <h3>Caffeine 계층 구조 (match-snapshot)</h3>
 * <pre>
 *   요청 → Caffeine (L1, JVM 메모리, ~0ms)
 *            ↓ miss
 *          Redis (L2, 네트워크 캐시, ~1ms)
 *            ↓ miss
 *          DB (fetch join, ~10ms)
 * </pre>
 *
 * <h3>seat-remaining 캐시 구조</h3>
 * <pre>
 *   요청 → Caffeine (L1, 2초 TTL, ~0ms)
 *            ↓ miss (2초마다 1회)
 *          Redis Hash HGETALL (원본, ~1ms)
 *
 *   sync=true: TTL 만료 시 단 1개 스레드만 Redis 조회 → Thundering Herd 방지
 * </pre>
 *
 * <h3>Redis 장애 시 동작 (match-snapshot)</h3>
 * <ul>
 *   <li>Caffeine L1 히트 → Redis 없이도 서비스 유지.</li>
 *   <li>Caffeine L1 미스 → DB 직접 조회 (Redis 기록 생략).</li>
 *   <li>Redis 복구 후 → 다음 캐시 미스 시 Redis 에 자동 재적재.</li>
 * </ul>
 *
 * <h3>Cache Stampede / Thundering Herd 방지</h3>
 * <ul>
 *   <li>match-snapshot: Caffeine {@code computeIfAbsent} + {@code @Cacheable(sync=true)} 로 단일 로더 보장.</li>
 *   <li>seat-remaining: Caffeine {@code computeIfAbsent} + {@code @Cacheable(sync=true)} 로 2초마다 Redis 조회 1회로 제한.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class MatchCacheConfig implements CachingConfigurer {

    public static final String MATCH_SNAPSHOT_CACHE = "match-snapshot";

    /**
     * 잔여 좌석 표시용 캐시.
     *
     * <p>Redis Hash({@code seat:remaining:{matchId}}) 가 원본이므로 L2(Redis) 캐시는 사용하지 않는다.
     * Caffeine L1 만 사용하며, TTL 2초로 Thundering Herd 를 완화한다.
     * 사용 시 {@code @Cacheable(cacheManager = "caffeineCacheManager")} 로 지정해
     * {@link CompositeCacheManager} 를 우회해야 한다.
     */
    public static final String SEAT_REMAINING_CACHE = "seat-remaining";

    // ── L1: Caffeine (캐시별 TTL 분리) ───────────────────────────────────────

    /**
     * Caffeine L1 캐시 매니저.
     *
     * <p>캐시마다 TTL 이 다르므로 {@link CaffeineCache} 를 직접 생성하고
     * {@link SimpleCacheManager} 로 묶는다.
     *
     * <ul>
     *   <li>{@code match-snapshot}: TTL 5분 — Redis L2 보다 짧게 설정해 L1 만료 후 L2 에서 최신 값 확인</li>
     *   <li>{@code seat-remaining}: TTL 2초 — 표시용 허용 오차, Redis 조회를 2초당 1회로 제한</li>
     * </ul>
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCache matchSnapshotCache = new CaffeineCache(
                MATCH_SNAPSHOT_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(1_000)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()
                        .build()
        );

        CaffeineCache seatRemainingCache = new CaffeineCache(
                SEAT_REMAINING_CACHE,
                Caffeine.newBuilder()
                        .maximumSize(1_000)                    // 경기 1,000개 상한
                        .expireAfterWrite(Duration.ofSeconds(2)) // 표시용 허용 오차 2초
                        .recordStats()
                        .build()
        );

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(matchSnapshotCache, seatRemainingCache));
        return manager;
    }

    // ── L2: Redis ─────────────────────────────────────────────────────────────

    /**
     * Redis L2 캐시 매니저 ({@code match-snapshot} 전용).
     *
     * <p>TTL 60분: {@code @CacheEvict} 가 정확성을 보장하므로 TTL 은 안전망.
     * {@code seat-remaining} 은 Redis 가 원본이므로 이 매니저에 등록하지 않는다.
     */
    @Bean("redisCacheManager")
    public CacheManager redisCacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(60))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .withCacheConfiguration(MATCH_SNAPSHOT_CACHE, config)
                // seat-remaining 은 Redis 원본이므로 L2 캐시 미등록
                .build();
    }

    // ── Composite: L1 → L2 순서 조회 (match-snapshot 전용) ───────────────────

    /**
     * {@link CompositeCacheManager}: L1(Caffeine) → L2(Redis) 순서로 조회.
     *
     * <p>{@code match-snapshot} 에만 사용한다.
     * {@code seat-remaining} 은 {@code @Cacheable(cacheManager = "caffeineCacheManager")} 로
     * 이 매니저를 우회하여 Caffeine 전용으로 동작한다.
     */
    @Bean
    public CacheManager cacheManager(
            CacheManager caffeineCacheManager,
            CacheManager redisCacheManager
    ) {
        CompositeCacheManager composite = new CompositeCacheManager(
                caffeineCacheManager,
                redisCacheManager
        );
        composite.setFallbackToNoOpCache(false);
        return composite;
    }

    // ── Redis 오류 핸들러 ─────────────────────────────────────────────────────

    /**
     * Redis 장애 시 예외를 삼키고 DB 또는 L1 캐시로 폴백한다.
     *
     * <p>기본 {@link SimpleCacheErrorHandler} 는 예외를 그대로 전파하여 서비스 장애로 이어진다.
     * 이 핸들러는 Redis 오류를 WARN 로그로만 기록하고 계속 진행한다.
     */
    @Bean
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] GET 실패 — cache={}, key={}, 원인={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] PUT 실패 — cache={}, key={}, 원인={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] EVICT 실패 — cache={}, key={}, 원인={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] CLEAR 실패 — cache={}, 원인={}", cache.getName(), e.getMessage());
            }
        };
    }
}
