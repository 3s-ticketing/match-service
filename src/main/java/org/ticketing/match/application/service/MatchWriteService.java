package org.ticketing.match.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.match.application.dto.command.AddMatchZonePolicyCommand;
import org.ticketing.match.application.dto.command.ChangeMatchStatusCommand;
import org.ticketing.match.application.dto.command.DeleteMatchCommand;
import org.ticketing.match.application.dto.command.RemoveMatchZonePolicyCommand;
import org.ticketing.match.application.dto.command.UpdateMatchCommand;
import org.ticketing.match.application.dto.command.UpdateMatchZonePolicyCommand;
import org.ticketing.match.application.dto.result.MatchResult;
import org.ticketing.match.application.dto.result.MatchZonePolicyResult;
import org.ticketing.match.domain.exception.MatchNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.model.MatchZonePolicy;
import org.ticketing.match.domain.repository.MatchRepository;

/**
 * Match 어그리게이트 쓰기 전담 서비스.
 *
 * <p>모든 메서드는 {@code @Transactional} 아래에서 실행되므로
 * 미래에 Outbox 저장, ZonePolicy 추가 등 쓰기가 여러 건이 생겨도
 * 단일 트랜잭션으로 원자성이 보장된다.
 *
 * <p>{@link MatchApplicationService} 에서 Feign 검증을 마친 뒤
 * 이 서비스로 위임하면, DB 커넥션을 잡은 채 외부 HTTP 를 기다리는
 * 문제(커넥션 풀 고갈)가 해소된다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MatchWriteService {

    private final MatchRepository matchRepository;

    // ──────────────────────────────────────────
    // Match 생성
    // ──────────────────────────────────────────

    /**
     * Match 도메인 객체를 받아 저장한다.
     *
     * <p>외부 서비스 검증(Feign)은 호출자({@link MatchApplicationService#createMatch})가
     * 트랜잭션 없이 먼저 수행하며, 이 메서드는 순수하게 영속화만 담당한다.
     * 추후 Outbox 이벤트 저장, 초기 ZonePolicy 생성 등이 추가되어도
     * 같은 트랜잭션 안에서 원자적으로 처리된다.
     */
    public MatchResult create(Match match) {
        return MatchResult.from(matchRepository.save(match));
    }

    // ──────────────────────────────────────────
    // Match 수정
    // ──────────────────────────────────────────

    public MatchResult update(UpdateMatchCommand command) {
        Match match = getActive(command.matchId());
        match.update(command.name(), command.matchDatetime(), command.ticketOpenAt());
        return MatchResult.from(match);
    }

    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        Match match = getActive(command.matchId());
        match.changeStatus(command.targetStatus());
        return MatchResult.from(match);
    }

    public void delete(DeleteMatchCommand command) {
        Match match = getActive(command.matchId());
        match.delete(command.deletedBy());
    }

    // ──────────────────────────────────────────
    // ZonePolicy
    // ──────────────────────────────────────────

    public MatchZonePolicyResult addZonePolicy(AddMatchZonePolicyCommand command) {
        Match match = getActive(command.matchId());
        // 1차: 도메인 가드 (인메모리 중복 검사 → DuplicateMatchZonePolicyException)
        MatchZonePolicy policy = match.addZonePolicy(command.seatGradeId(), command.price());
        // 2차: saveAndFlush 로 즉시 플러시 → DB 유니크 제약 위반 시 트랜잭션 안에서 즉시 감지
        matchRepository.saveAndFlush(match);
        return MatchZonePolicyResult.from(policy);
    }

    public MatchZonePolicyResult updateZonePolicy(UpdateMatchZonePolicyCommand command) {
        Match match = getActive(command.matchId());
        match.updateZonePolicy(command.policyId(), command.price());
        return MatchZonePolicyResult.from(match.findZonePolicy(command.policyId()));
    }

    public void removeZonePolicy(RemoveMatchZonePolicyCommand command) {
        Match match = getActive(command.matchId());
        match.removeZonePolicy(command.policyId(), command.deletedBy());
    }

    // ──────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────

    private Match getActive(java.util.UUID matchId) {
        return matchRepository.findActiveById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
    }
}
