package org.ticketing.match.application.service;

import java.time.OffsetDateTime;
import java.util.UUID;
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
import org.ticketing.match.domain.event.MatchEventPublisher;
import org.ticketing.match.domain.event.payload.MatchApprovedEvent;
import org.ticketing.match.domain.event.payload.MatchCanceledEvent;
import org.ticketing.match.domain.exception.MatchNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.model.MatchStatus;
import org.ticketing.match.domain.model.MatchZonePolicy;
import org.ticketing.match.domain.repository.MatchRepository;

/**
 * Match 어그리게이트 쓰기 전담 서비스.
 *
 * <p>모든 메서드는 {@code @Transactional} 아래에서 실행되므로
 * 도메인 변경 + Outbox 저장이 단일 트랜잭션으로 원자적으로 처리된다.
 *
 * <p>이벤트 발행 흐름:
 * <ol>
 *   <li>{@link MatchEventPublisher} → {@code Events.trigger()} 호출</li>
 *   <li>{@code OutboxEventListener.recordOutbox()} 가 동일 트랜잭션에서 Outbox 레코드 저장</li>
 *   <li>트랜잭션 커밋 후 {@code OutboxEventListener.publish()} 가 Kafka 로 전송</li>
 * </ol>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MatchWriteService {

    private final MatchRepository matchRepository;
    private final MatchEventPublisher matchEventPublisher;

    // ──────────────────────────────────────────
    // Match 생성
    // ──────────────────────────────────────────

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

    /**
     * 상태 변경 + 이벤트 발행.
     *
     * <ul>
     *   <li>APPROVED → {@code match.approved} : queue-service 가 소비하여 ticketOpenAt 캐시 초기화</li>
     *   <li>CANCELED → {@code match.canceled} : reservation-service 가 소비하여 진행 중 예매 취소</li>
     * </ul>
     */
    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        Match match = getActive(command.matchId());
        match.changeStatus(command.targetStatus());

        if (command.targetStatus() == MatchStatus.APPROVED) {
            matchEventPublisher.publishMatchApproved(
                    new MatchApprovedEvent(match.getId(), match.getTicketOpenAt())
            );
        } else if (command.targetStatus() == MatchStatus.CANCELED) {
            matchEventPublisher.publishMatchCanceled(
                    new MatchCanceledEvent(match.getId(), OffsetDateTime.now())
            );
        }

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
        MatchZonePolicy policy = match.addZonePolicy(command.seatGradeId(), command.price());
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

    private Match getActive(UUID matchId) {
        return matchRepository.findActiveById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
    }
}
