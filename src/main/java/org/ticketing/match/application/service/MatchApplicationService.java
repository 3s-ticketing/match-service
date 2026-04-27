package org.ticketing.match.application.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.common.event.Events;
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

    private static final String DOMAIN_TYPE          = "MATCH";
    private static final String TOPIC_MATCH_CANCELED = "match.canceled";

    private final MatchRepository matchRepository;
    private final ClubProvider clubProvider;
    private final StadiumProvider stadiumProvider;

    // ──────────────────────────────────────────
    // Match CRUD
    // ──────────────────────────────────────────

    /**
     * 외부 서비스 검증(Feign)을 트랜잭션 바깥에서 실행한 뒤 저장만 트랜잭션으로 처리한다.
     * Feign 호출을 @Transactional 안에 두면 DB 커넥션을 잡은 채로 외부 HTTP 응답을
     * 기다리게 되어 커넥션 풀 고갈 및 장애 전파 위험이 있다.
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
     * CANCELED 전이 시 common-module 의 Outbox 패턴을 통해 이벤트를 발행한다.
     *
     * <p>흐름:
     * 1. Events.trigger() → Spring ApplicationEvent(OutboxEvent) 발행
     * 2. OutboxEventListener.recordOutbox() (@EventListener) → 같은 트랜잭션 내에서 P_OUTBOX 저장
     * 3. OutboxEventListener.publish() (@TransactionalEventListener AFTER_COMMIT) → Kafka 발행
     * 4. 실패 시 OutboxRelayScheduler 가 PENDING/FAILED 레코드 재시도, 3회 초과 시 DLT 격리
     *
     * <p>correlationId 는 "match:{matchId}:canceled" 형태의 결정적 값을 사용한다.
     * UUID.randomUUID() 를 쓰면 동일 경기 취소 요청이 두 번 들어올 때 서로 다른
     * correlationId 로 인식되어 Outbox 중복 저장 방지가 무력화된다.
     * 결정적 correlationId 를 사용하면 같은 경기의 두 번째 취소 이벤트는
     * OutboxEventListener 의 exists() 체크에서 차단된다.
     */
    @Transactional
    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        Match match = matchRepository.findActiveById(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));

        match.changeStatus(command.targetStatus());

        if (command.targetStatus() == MatchStatus.CANCELED) {
            Events.trigger(
                    "match:" + match.getId() + ":canceled", // correlationId — Outbox 중복 저장 방지 키
                    DOMAIN_TYPE,                            // domainType
                    match.getId().toString(),               // domainId (Kafka message key)
                    TOPIC_MATCH_CANCELED,                   // eventType = Kafka topic
                    new MatchCanceledEvent(match.getId(), OffsetDateTime.now())
            );
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
