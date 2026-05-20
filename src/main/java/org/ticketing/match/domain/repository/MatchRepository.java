package org.ticketing.match.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ticketing.match.domain.model.Match;

public interface MatchRepository {

    Match save(Match match);

    /**
     * 저장 후 즉시 플러시한다.
     * addZonePolicy 처럼 DB 유니크 제약 위반을 트랜잭션 안에서 즉시 감지해야 할 때 사용한다.
     */
    Match saveAndFlush(Match match);

    Optional<Match> findById(UUID id);

    Optional<Match> findActiveById(UUID id);

    /**
     * ZonePolicy 를 fetch join 으로 함께 조회한다.
     * MatchSnapshotCacheService 가 캐시 미스 시에만 호출한다.
     */
    Optional<Match> findActiveByIdWithPolicies(UUID id);
}
