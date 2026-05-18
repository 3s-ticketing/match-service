package org.ticketing.match.infrastructure.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Spring Cache → Redis 연동 설정.
 *
 * <p>캐시 이름별 TTL:
 * <ul>
 *   <li>{@code match-snapshot} : 10분 — Match + ZonePolicy 정적 데이터 (관리자 변경 시 evict)</li>
 * </ul>
 *
 * <p>직렬화: {@link GenericJackson2JsonRedisSerializer} (JSON + 타입 정보 포함)
 * → record 타입을 역직렬화할 때도 정확한 타입으로 복원된다.
 */
@Configuration
@EnableCaching
public class MatchCacheConfig {

    public static final String MATCH_SNAPSHOT_CACHE = "match-snapshot";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        RedisCacheConfiguration snapshotConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(10));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(MATCH_SNAPSHOT_CACHE, snapshotConfig)
                .build();
    }
}
