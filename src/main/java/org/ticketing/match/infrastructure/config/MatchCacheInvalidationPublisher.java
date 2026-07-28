package org.ticketing.match.infrastructure.config;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * match-snapshot L1(Caffeine) 캐시 무효화를 Redis Pub/Sub 으로 다른 인스턴스에 전파한다.
 *
 * <h3>배경</h3>
 * <p>{@code match-service} 는 고가용성을 위해 여러 인스턴스로 수평 복제되어 운영된다.
 * L1(Caffeine) 은 인스턴스마다 격리된 JVM 로컬 캐시이므로, 한 인스턴스에서
 * {@link org.ticketing.match.application.service.MatchSnapshotCacheService#evict} 가 호출되어도
 * 다른 인스턴스의 L1 은 그대로 남아 최대 TTL(5분) 동안 stale 한 스냅샷을 반환할 수 있다.
 *
 * <h3>해결</h3>
 * <p>evict 시 matchId 를 Redis 채널({@value #CHANNEL})로 발행하여, 모든 인스턴스에서 구동 중인
 * {@link MatchCacheInvalidationListener} 가 각자의 로컬 L1 캐시를 함께 무효화하도록 한다.
 * L2(Redis) 는 모든 인스턴스가 공유하는 원본이므로 별도 전파가 필요 없다.
 *
 * <h3>장애 허용</h3>
 * <p>발행 실패(Redis 장애 등)는 WARN 로그만 남기고 무시한다. L1 TTL(5분)이 안전망 역할을 하므로,
 * 발행 실패가 곧바로 데이터 정합성 문제로 이어지지는 않는다.
 */
@Slf4j
@RequiredArgsConstructor
public class MatchCacheInvalidationPublisher {

    public static final String CHANNEL = "match-snapshot-cache-evict";

    private final StringRedisTemplate redisTemplate;

    public void publishEvict(UUID matchId) {
        try {
            redisTemplate.convertAndSend(CHANNEL, matchId.toString());
            log.debug("[Cache] 무효화 이벤트 발행 — channel={}, matchId={}", CHANNEL, matchId);
        } catch (RuntimeException e) {
            log.warn("[Cache] 무효화 이벤트 발행 실패 — channel={}, matchId={}, 원인={}",
                    CHANNEL, matchId, e.getMessage());
        }
    }
}
