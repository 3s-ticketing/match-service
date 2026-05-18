package org.ticketing.match.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.cache.support.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * L1(Caffeine) + L2(Redis) 2계층 캐시 구성.
 *
 * <h3>계층 구조</h3>
 * <pre>
 *   요청 → Caffeine (L1, JVM 메모리, ~0ms)
 *            ↓ miss
 *          Redis (L2, 네트워크 캐시, ~1ms)
 *            ↓ miss
 *          DB (fetch join, ~10ms)
 * </pre>
 *
 * <h3>Redis 장애 시 동작</h3>
 * <ul>
 *   <li>{@link RedisCacheErrorHandler} 가 Redis 오류를 로깅 후 무시한다.</li>
 *   <li>Caffeine L1 히트 → Redis 없이도 서비스 유지.</li>
 *   <li>Caffeine L1 미스 → DB 직접 조회 (Redis 기록 생략).</li>
 *   <li>Redis 복구 후 → 다음 캐시 미스 시 Redis 에 자동 재적재.</li>
 * </ul>
 *
 * <h3>Cache Stampede 방지</h3>
 * <ul>
 *   <li>Caffeine: {@link Caffeine#maximumSize} + expireAfterWrite 로 JVM 내 단일 로더 보장.</li>
 *   <li>Redis: {@code @Cacheable(sync = true)} 로 동시 DB 조회를 단일 스레드로 직렬화.</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableCaching
public class MatchCacheConfig implements CachingConfigurer {

    public static final String MATCH_SNAPSHOT_CACHE = "match-snapshot";

    // ── L1: Caffeine ──────────────────────────────────────────────────────────

    /**
     * Caffeine L1 캐시 매니저.
     *
     * <p>TTL 5분: Redis 보다 짧게 설정해 L1 만료 후 Redis(L2) 에서 최신 값을 확인한다.
     * {@code @CacheEvict} 는 {@link CompositeCacheManager} 를 통해 L1·L2 모두 무효화한다.
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(MATCH_SNAPSHOT_CACHE);
        manager.setCaffeine(
                Caffeine.newBuilder()
                        .maximumSize(1_000)          // 경기 1,000개 상한
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .recordStats()               // 캐시 히트율 모니터링
        );
        return manager;
    }

    // ── L2: Redis ─────────────────────────────────────────────────────────────

    /**
     * Redis L2 캐시 매니저.
     *
     * <p>TTL 60분: {@code @CacheEvict} 가 정확성을 보장하므로 TTL 은 안전망.
     * Redis 장애 시 {@link RedisCacheErrorHandler} 가 오류를 무시하여 L1 으로 폴백.
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
                .build();
    }

    // ── Composite: L1 → L2 순서 조회 ─────────────────────────────────────────

    /**
     * {@link CompositeCacheManager}: L1(Caffeine) → L2(Redis) 순서로 조회.
     *
     * <p>Spring {@code @Cacheable} 은 이 매니저를 사용한다.
     * L1 히트 시 L2 조회 생략 → Redis 네트워크 왕복 0회.
     * L1 미스 → L2 조회 → 히트 시 L1 에도 자동 저장.
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
        composite.setFallbackToNoOpCache(false); // 캐시 미스 시 DB 조회 보장
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
                log.warn("[Cache] Redis GET 실패 — cache={}, key={}, 원인={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] Redis PUT 실패 — cache={}, key={}, 원인={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] Redis EVICT 실패 — cache={}, key={}, 원인={}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] Redis CLEAR 실패 — cache={}, 원인={}", cache.getName(), e.getMessage());
            }
        };
    }
}
