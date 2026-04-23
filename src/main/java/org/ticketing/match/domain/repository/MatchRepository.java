package org.ticketing.match.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ticketing.match.domain.model.Match;

public interface MatchRepository {

    Match save(Match match);

    Optional<Match> findById(UUID id);

    Optional<Match> findByIdAndDeletedAtIsNull(UUID id);
}
