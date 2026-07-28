package org.ticketing.match.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import static org.ticketing.match.infrastructure.config.MatchCacheConfig.MATCH_SNAPSHOT_CACHE;

/**
 * 다른 인스턴스가 Redis 채널({@value MatchCacheInvalidationPublisher#CHANNEL})로 발행한
 * match-snapshot 무효화 이벤트를 구독해, 자신의 로컬 L1(Caffeine) 캐시를 무효화한다.
 *
 * <p>L2(Redis) 는 모든 인스턴스가 공유하는 원본이므로 이 리스너의 대상이 아니다 —
 * 오직 인스턴스별로 격리되어 원격에서는 직접 건드릴 수 없는 L1 캐시만을 무효화한다.
 */
@Slf4j
public class MatchCacheInvalidationListener implements MessageListener {

    private final CacheManager caffeineCacheManager;

    public MatchCacheInvalidationListener(
            @Qualifier("caffeineCacheManager") CacheManager caffeineCacheManager
    ) {
        this.caffeineCacheManager = caffeineCacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String matchIdRaw = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            UUID matchId = UUID.fromString(matchIdRaw);
            Cache cache = caffeineCacheManager.getCache(MATCH_SNAPSHOT_CACHE);
            if (cache != null) {
                cache.evict(matchId);
                log.debug("[Cache] 원격 무효화 수신 → L1-Caffeine evict — matchId={}", matchId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("[Cache] 무효화 메시지 파싱 실패 — payload={}", matchIdRaw);
        }
    }
}
