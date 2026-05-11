package org.ticketing.match.application.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import org.ticketing.match.domain.repository.SeatAvailabilityRepository;

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
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MatchWriteService {

    private final MatchRepository matchRepository;
    private final MatchEventPublisher matchEventPublisher;
    private final SeatAvailabilityRepository seatAvailabilityRepository;

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
     *   <li>APPROVED → {@code match.approved} : reservation-service 가 소비하여 ticketOpenAt 캐시 초기화</li>
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
            // APPROVED 전환 시 Redis 잔여 좌석 초기화 — DB 커밋 후 실행하여 정합성 보장
            //
            // merge function: 동일 seatGradeId 를 가진 활성 ZonePolicy 가 둘 이상이면
            // 도메인 불변식 위반이므로 경고 로그를 남기고 첫 번째 값을 사용한다.
            UUID matchId = match.getId();
            Map<UUID, Long> seatCounts = match.getZonePolicies().stream()
                    .filter(p -> p.getDeletedAt() == null)
                    .collect(Collectors.toMap(
                            MatchZonePolicy::getSeatGradeId,
                            MatchZonePolicy::getTotalSeatCount,
                            (existing, duplicate) -> {
                                log.warn("[MatchWriteService] matchId={} — seatGradeId 중복 ZonePolicy 감지. "
                                        + "도메인 불변식 위반 가능성. 첫 번째 값({}) 사용.", matchId, existing);
                                return existing;
                            }
                    ));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Redis 초기화 실패 시 match 는 이미 APPROVED 로 커밋된 상태.
                    // 실패를 에러 로그로 기록하여 운영 알람 및 수동 재초기화가 가능하도록 한다.
                    // fallback: getRemainingSeats() 는 Redis 가 비어 있으면 totalSeatCount 를 반환하므로
                    // 서비스 중단은 없지만 실시간 카운트 정확도가 떨어진다.
                    try {
                        seatAvailabilityRepository.initialize(matchId, seatCounts);
                        log.info("[MatchWriteService] matchId={} Redis 잔여 좌석 초기화 완료. zones={}",
                                matchId, seatCounts.size());
                    } catch (Exception e) {
                        log.error("[MatchWriteService] matchId={} Redis 잔여 좌석 초기화 실패. "
                                + "APPROVED 커밋은 완료됐으나 Redis 가 비어 있음. "
                                + "수동 재초기화 또는 재승인 처리 필요.", matchId, e);
                    }
                }
            });
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

    public MatchZonePolicyResult addZonePolicy(AddMatchZonePolicyCommand command, long totalSeatCount) {
        Match match = getActive(command.matchId());
        MatchZonePolicy policy = match.addZonePolicy(command.seatGradeId(), command.price(), totalSeatCount);
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
