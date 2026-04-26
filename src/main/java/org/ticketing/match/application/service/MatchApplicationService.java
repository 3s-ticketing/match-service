package org.ticketing.match.application.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
import org.ticketing.match.domain.event.MatchEventPublisher;
import org.ticketing.match.domain.event.payload.MatchCanceledEvent;
import org.ticketing.match.domain.exception.ClubNotFoundException;
import org.ticketing.match.domain.exception.MatchNotFoundException;
import org.ticketing.match.domain.exception.StadiumNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.model.MatchStatus;
import org.ticketing.match.domain.model.MatchZonePolicy;
import org.ticketing.match.domain.repository.MatchRepository;
import org.ticketing.match.domain.service.ClubProvider;
import org.ticketing.match.domain.service.StadiumProvider;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchApplicationService {

    private final MatchRepository matchRepository;
    private final MatchEventPublisher matchEventPublisher;
    private final ClubProvider clubProvider;
    private final StadiumProvider stadiumProvider;

    // ──────────────────────────────────────────
    // Match CRUD
    // ──────────────────────────────────────────

    /**
     * 외부 서비스 검증(Feign)을 트랜잭션 바깥에서 실행한 뒤 저장만 트랜잭션으로 처리한다.
     * Feign 호출을 @Transactional 안에 두면 DB 커넥션을 잡은 채로 외부 HTTP 응답을
     * 기다리게 되어 커넥션 풀 고갈 및 장애 전파 위험이 있다.
     * 실제 저장은 JpaRepository.save() 의 자체 @Transactional 이 처리한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public MatchResult createMatch(CreateMatchCommand command) {
        // 1. 외부 서비스 검증 — 트랜잭션 없음
        if (!clubProvider.existsById(command.homeClubId())) {
            throw new ClubNotFoundException(command.homeClubId());
        }
        if (!clubProvider.existsById(command.awayClubId())) {
            throw new ClubNotFoundException(command.awayClubId());
        }
        if (!stadiumProvider.existsById(command.stadiumId())) {
            throw new StadiumNotFoundException(command.stadiumId());
        }

        // 2. 저장 — JpaRepository.save() 의 @Transactional 로 처리
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
        Match match = matchRepository.findActiveById(query.matchId())
                .orElseThrow(() -> new MatchNotFoundException(query.matchId()));
        return MatchResult.from(match);
    }

    @Transactional
    public MatchResult updateMatch(UpdateMatchCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.update(command.name(), command.matchDatetime(), command.ticketOpenAt());
        return MatchResult.from(match);
    }

    /**
     * 상태 변경 처리.
     * CANCELED 전이 시 예매·결제 서비스가 소비할 MatchCanceledEvent 를 발행한다.
     * Kafka 발행이 트랜잭션 커밋 전에 실행되므로, Kafka 실패 시 트랜잭션이 롤백된다.
     * 프로덕션에서는 Outbox 패턴으로 교체해 커밋 후 발행을 보장해야 한다.
     */
    @Transactional
    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));

        MatchStatus previousStatus = match.getStatus();
        match.changeStatus(command.targetStatus());

        if (command.targetStatus() == MatchStatus.CANCELED) {
            matchEventPublisher.publishMatchCanceled(new MatchCanceledEvent(
                    match.getId(),
                    OffsetDateTime.now()
            ));
        }

        return MatchResult.from(match);
    }

    @Transactional
    public void deleteMatch(DeleteMatchCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.delete(command.deletedBy());
    }

    // ──────────────────────────────────────────
    // ZonePolicy — Match 어그리게이트를 통한 접근
    // ──────────────────────────────────────────

    @Transactional
    public MatchZonePolicyResult addZonePolicy(AddMatchZonePolicyCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        // 1차: 도메인 가드 (인메모리 중복 검사 → DuplicateMatchZonePolicyException)
        MatchZonePolicy policy = match.addZonePolicy(command.seatGradeId(), command.price());
        // 2차: saveAndFlush 로 즉시 플러시 → DB 유니크 제약 위반 시 트랜잭션 내에서 즉시 감지
        matchRepository.saveAndFlush(match);
        return MatchZonePolicyResult.from(policy);
    }

    @Transactional
    public MatchZonePolicyResult updateZonePolicy(UpdateMatchZonePolicyCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.updateZonePolicy(command.policyId(), command.price());
        return MatchZonePolicyResult.from(match.findZonePolicy(command.policyId()));
    }

    @Transactional
    public void removeZonePolicy(RemoveMatchZonePolicyCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        match.removeZonePolicy(command.policyId(), command.deletedBy());
    }
}
