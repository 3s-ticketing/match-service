package org.ticketing.match.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ticketing.match.domain.model.Match;

public interface JpaMatchRepository extends JpaRepository<Match, UUID> {

    Optional<Match> findByIdAndDeletedAtIsNull(UUID id);
}
