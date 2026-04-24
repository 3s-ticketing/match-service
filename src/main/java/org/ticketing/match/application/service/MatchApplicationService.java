package org.ticketing.match.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.match.application.dto.command.AddMatchZonePolicyCommand;
import org.ticketing.match.application.dto.command.ChangeMatchStatusCommand;
import org.ticketing.match.application.dto.command.CreateMatchCommand;
import org.ticketing.match.application.dto.command.DeleteMatchCommand;
import org.ticketing.match.application.dto.command.RemoveMatchZonePolicyCommand;
import org.ticketing.match.application.dto.command.UpdateMatchCommand;
import org.ticketing.match.application.dto.command.UpdateMatchZonePolicyCommand;
import org.ticketing.match.application.dto.query.FindMatchQuery;
import org.ticketing.match.application.dto.result.MatchResult;
import org.ticketing.match.application.dto.result.MatchZonePolicyResult;
import org.ticketing.match.domain.exception.ClubNotFoundException;
import org.ticketing.match.domain.exception.MatchNotFoundException;
import org.ticketing.match.domain.exception.StadiumNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.repository.MatchRepository;
import org.ticketing.match.domain.service.ClubProvider;
import org.ticketing.match.domain.service.StadiumProvider;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchApplicationService {

    private final MatchRepository matchRepository;
    private final ClubProvider clubProvider;
    private final StadiumProvider stadiumProvider;

    // ──────────────────────────────────────────
    // Match CRUD
    // ──────────────────────────────────────────

    @Transactional
    public MatchResult createMatch(CreateMatchCommand command) {
        if (!clubProvider.existsById(command.homeClubId())) {
            throw new ClubNotFoundException(command.homeClubId());
        }
        if (!clubProvider.existsById(command.awayClubId())) {
            throw new ClubNotFoundException(command.awayClubId());
        }
        if (!stadiumProvider.existsById(command.stadiumId())) {
            throw new StadiumNotFoundException(command.stadiumId());
        }

        Match match = Match.create(
                command.homeClubId(),
                command.awayClubId(),
                command.stadiumId(),
                command.name(),
                command.matchDatetime(),
                command.ticketOpenAt()
        );

        return MatchResult.from(matchRepository.save(match));
    }

    public MatchResult findMatch(FindMatchQuery query) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(query.matchId())
                .orElseThrow(() -> new MatchNotFoundException(query.matchId()));
        return MatchResult.from(match);
    }

    @Transactional
    public MatchResult updateMatch(UpdateMatchCommand command) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.update(command.name(), command.matchDatetime(), command.ticketOpenAt());
        return MatchResult.from(match);
    }

    @Transactional
    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.changeStatus(command.targetStatus());
        return MatchResult.from(match);
    }

    @Transactional
    public void deleteMatch(DeleteMatchCommand command) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.delete(command.deletedBy());
    }

    // ──────────────────────────────────────────
    // ZonePolicy — Match 어그리게이트를 통한 접근
    // ──────────────────────────────────────────

    @Transactional
    public MatchZonePolicyResult addZonePolicy(AddMatchZonePolicyCommand command) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        matchRepository.save(match);
        return MatchZonePolicyResult.from(match.addZonePolicy(command.seatGradeId(), command.price()));
    }

    @Transactional
    public MatchZonePolicyResult updateZonePolicy(UpdateMatchZonePolicyCommand command) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.updateZonePolicy(command.policyId(), command.price());
        return MatchZonePolicyResult.from(match.findZonePolicy(command.policyId()));
    }

    @Transactional
    public void removeZonePolicy(RemoveMatchZonePolicyCommand command) {
        Match match = matchRepository.findByIdAndDeletedAtIsNull(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.removeZonePolicy(command.policyId(), command.deletedBy());
    }
}
