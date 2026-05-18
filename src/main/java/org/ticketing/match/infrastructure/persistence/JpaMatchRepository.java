package org.ticketing.match.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ticketing.match.domain.model.Match;

public interface JpaMatchRepository extends JpaRepository<Match, UUID> {

    @Query("select m from Match m where m.id = :id and m.deletedAt is null")
    Optional<Match> findActiveById(@Param("id") UUID id);

    /**
     * ZonePolicy 를 즉시 로딩(fetch join)해 N+1 쿼리를 방지한다.
     * MatchSnapshotCacheService 의 캐시 미스 시 단 1회 호출된다.
     */
    @Query("select m from Match m join fetch m.zonePolicies where m.id = :id and m.deletedAt is null")
    Optional<Match> findActiveByIdWithPolicies(@Param("id") UUID id);
}
