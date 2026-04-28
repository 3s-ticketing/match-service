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
}
