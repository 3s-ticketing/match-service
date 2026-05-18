package org.ticketing.match.application.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.match.application.dto.result.MatchSnapshot;
import org.ticketing.match.domain.exception.MatchNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.repository.MatchRepository;

import static org.ticketing.match.infrastructure.config.MatchCacheConfig.MATCH_SNAPSHOT_CACHE;

/**
 * Match + ZonePolicy 데이터를 Redis 에 캐싱하는 서비스.
 *
 * <h3>캐시 전략</h3>
 * <ul>
 *   <li>캐시 히트 : Redis 에서 {@link MatchSnapshot} 을 즉시 반환 (DB 쿼리 0회)</li>
 *   <li>캐시 미스 : fetch join 으로 Match + ZonePolicies 를 단 1번 DB 조회 후 Redis 에 저장</li>
 *   <li>쓰기(update/delete/addZonePolicy 등) 후 {@link #evict} 호출로 캐시 무효화</li>
 * </ul>
 *
 * <h3>주의</h3>
 * <p>캐시 어드바이스({@code @Cacheable})는 Spring AOP 프록시를 통해서만 동작한다.
 * 같은 클래스 내 자기 호출(self-invocation)에는 적용되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MatchSnapshotCacheService {

    private final MatchRepository matchRepository;

    /**
     * Match 스냅샷 조회 (캐시 우선).
     *
     * <p>{@code @Transactional(readOnly = true)} 는 캐시 미스 시 DB 조회에만 필요하다.
     * 캐시 히트 시에는 트랜잭션이 열리지 않아 DB 커넥션을 소비하지 않는다.
     */
    @Cacheable(value = MATCH_SNAPSHOT_CACHE, key = "#matchId")
    @Transactional(readOnly = true)
    public MatchSnapshot getSnapshot(UUID matchId) {
        Match match = matchRepository.findActiveByIdWithPolicies(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        return MatchSnapshot.from(match);
    }

    /**
     * 캐시 무효화.
     *
     * <p>Match 또는 ZonePolicy 가 변경된 후 호출한다.
     * 다음 {@link #getSnapshot} 호출 시 DB 에서 최신 데이터를 다시 로드한다.
     */
    @CacheEvict(value = MATCH_SNAPSHOT_CACHE, key = "#matchId")
    public void evict(UUID matchId) {
        // 어노테이션이 캐시 항목을 삭제한다. 메서드 본문은 비워도 된다.
    }
}
