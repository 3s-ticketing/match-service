package org.ticketing.match.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 *                     {@link TwoLevelCache} 가 L1 미스 시 L2 를 실제로 조회한다.
 *
 *   seat-remaining  : 구역별 잔여 좌석 수. L1(Caffeine 2초) → Redis(원본) 구조.
 *                     {@code @Cacheable(sync=true)} + {@code expireAfterWrite(2s)} 로 stampede 방지.
 *                     Redis 가 원본이므로 L2 Redis 캐시 미등록.
 * </pre>
 *
 * <h3>read-through 흐름 (match-snapshot)</h3>
 * <pre>
 *   요청 → TwoLevelCache
 *            ├─ L1(Caffeine) 히트 → 즉시 반환 (~0ms)
 *            ├─ L1 미스 + L2(Redis) 히트 → L1 역적재 후 반환 (~1ms)
 *            └─ L1·L2 모두 미스 → DB fetch join → L2 저장 → L1 저장 (~10ms)
 * </pre>
 *
 * <h3>Redis 장애 시 동작 (match-snapshot)</h3>
 * <ul>
 *   <li>L1 히트 → Redis 없이도 서비스 유지.</li>
 *   <li>L1 미스 → {@link TwoLevelCache} 내 L2 예외를 catch 후 DB 직접 조회.</li>
 *   <li>Redis 복구 후 → 다음 캐시 미스 시 Redis 에 자동 재적재.</li>
 * </ul>
 *
 * <h3>Cache Stampede 방지 (match-snapshot)</h3>
 * <ul>
 *   <li>Caffeine {@code computeIfAbsent} + {@code @Cacheable(sync=true)} 로 단일 로더 보장.</li>
 *   <li>단일 로더 내부에서 L2 를 먼저 확인하므로 DB 부하를 최소화한다.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class MatchCacheConfig implements CachingConfigurer {

    public static final String MATCH_SNAPSHOT_CACHE = "match-snapshot";
    public static final String SEAT_REMAINING_CACHE = "seat-remaining";

    // ── L1: Caffeine ─────────────────────────────────────────────────────────

    /**
     * Caffeine L1 캐시 매니저.
     *
     * <ul>
     *   <li>{@code match-snapshot}: TTL 5분 — Redis L2(60분) 보다 짧게 설정해 L1 만료 후 L2 에서 최신 값을 확인.</li>
     *   <li>{@code seat-remaining}: TTL 2초 — reservation-service 의 Redis DECR/INCR 이 원본.
     *       {@code @Cacheable(sync=true)} 로 stampede 방지. Redis L2 미등록(원본이 Redis 이므로).</li>
     * </ul>
     *
     * <p>이 빈은 {@link org.ticketing.match.application.service.MatchSnapshotCacheService#evict}
     * 에서 L1 직접 evict 시에도 사용된다.
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
                        .maximumSize(1_000)
                        .expireAfterWrite(Duration.ofSeconds(2))
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
     *
     * <p>이 빈은 {@link org.ticketing.match.application.service.MatchSnapshotCacheService#evict}
     * 에서 L2 직접 evict 시에도 사용된다.
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

    // ── Primary: TwoLevelCache(match-snapshot) + Caffeine(seat-remaining) ────

    /**
     * 애플리케이션 전반에서 사용하는 Primary {@link CacheManager}.
     *
     * <ul>
     *   <li>{@code match-snapshot}: {@link TwoLevelCache} — L1(Caffeine) → L2(Redis) 실제 read-through.</li>
     *   <li>{@code seat-remaining}: {@link CaffeineCache} — L1 전용 (Redis 가 원본).</li>
     * </ul>
     *
     * <p>{@link TwoLevelCache} 는 L1·L2 의 실제 {@link Cache} 인스턴스를 공유하므로,
     * {@link org.ticketing.match.application.service.MatchSnapshotCacheService#evict} 에서
     * 각 매니저를 통해 직접 evict 하는 경우에도 동일한 캐시 객체가 무효화된다.
     */
    @Bean
    @Primary
    public CacheManager cacheManager(
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager,
            @Qualifier("redisCacheManager") CacheManager redisCacheManager
    ) {
        Cache caffeineSnapshot = caffeineCacheManager.getCache(MATCH_SNAPSHOT_CACHE);
        Cache redisSnapshot    = redisCacheManager.getCache(MATCH_SNAPSHOT_CACHE);
        Cache twoLevelSnapshot = new TwoLevelCache(MATCH_SNAPSHOT_CACHE, caffeineSnapshot, redisSnapshot);

        Cache caffeineRemaining = caffeineCacheManager.getCache(SEAT_REMAINING_CACHE);

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(twoLevelSnapshot, caffeineRemaining));
        return manager;
    }

    // ── Redis 오류 핸들러 ─────────────────────────────────────────────────────

    /**
     * Redis 장애 시 예외를 삼키고 DB 또는 L1 캐시로 폴백한다.
     *
     * <p>기본 {@link SimpleCacheErrorHandler} 는 예외를 그대로 전파하여 서비스 장애로 이어진다.
     * 이 핸들러는 Redis 오류를 WARN 로그로만 기록하고 계속 진행한다.
     *
     * <p>{@link TwoLevelCache} 내부의 L2 호출은 별도로 try-catch 처리되므로
     * 이 핸들러는 {@code @Cacheable} AOP 경계에서 발생하는 오류를 추가로 방어한다.
     *
     * <p>{@link CachingConfigurer} 구현체의 메서드는 Spring 캐시 인프라가 인터페이스를 통해
     * 직접 호출하므로 {@code @Bean} 등록이 필요 없다.
     * {@code @Bean}을 붙이면 공통 모듈 {@code KafkaConfig.errorHandler()} 와 빈 이름 충돌이 발생한다.
     */
    @Override
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
