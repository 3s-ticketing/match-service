package org.ticketing.match.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.repository.MatchRepository;

@Repository
@RequiredArgsConstructor
public class MatchRepositoryImpl implements MatchRepository {

    private final JpaMatchRepository jpaMatchRepository;

    @Override
    public Match save(Match match) {
        return jpaMatchRepository.save(match);
    }

    @Override
    public Optional<Match> findById(UUID id) {
        return jpaMatchRepository.findById(id);
    }

    @Override
    public Optional<Match> findByIdAndDeletedAtIsNull(UUID id) {
        return jpaMatchRepository.findByIdAndDeletedAtIsNull(id);
    }
}
