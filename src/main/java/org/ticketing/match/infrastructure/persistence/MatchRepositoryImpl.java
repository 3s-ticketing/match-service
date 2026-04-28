package org.ticketing.match.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.ticketing.match.domain.exception.DuplicateMatchZonePolicyException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.repository.MatchRepository;

@Repository
@RequiredArgsConstructor
public class MatchRepositoryImpl implements MatchRepository {

    private final JpaMatchRepository jpaMatchRepository;

    private static final String UNIQUE_CONSTRAINT_NAME = "uq_matchzonepolicy_match_seatgrade";

    @Override
    public Match save(Match match) {
        return jpaMatchRepository.save(match);
    }

    /**
     * 저장 후 즉시 플러시하여 DB 유니크 제약 위반을 트랜잭션 내에서 즉시 감지한다.
     * 동시 요청이 도메인 가드(1차)를 통과한 경우 DB(2차)에서 차단하고
     * DataIntegrityViolationException 을 도메인 예외로 번역한다.
     */
    @Override
    public Match saveAndFlush(Match match) {
        try {
            return jpaMatchRepository.saveAndFlush(match);
        } catch (DataIntegrityViolationException e) {
            if (isZonePolicyUniqueViolation(e)) {
                throw new DuplicateMatchZonePolicyException();
            }
            throw e;
        }
    }

    private boolean isZonePolicyUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e.getRootCause();
        return cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains(UNIQUE_CONSTRAINT_NAME);
    }

    @Override
    public Optional<Match> findById(UUID id) {
        return jpaMatchRepository.findById(id);
    }

    @Override
    public Optional<Match> findActiveById(UUID id) {
        return jpaMatchRepository.findActiveById(id);
    }
}
