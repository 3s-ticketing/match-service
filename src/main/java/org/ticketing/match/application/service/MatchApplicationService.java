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
import org.ticketing.match.domain.exception.SeatGradeNotFoundException;
import org.ticketing.match.domain.exception.StadiumNotFoundException;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.repository.MatchRepository;
import org.ticketing.match.domain.service.ClubProvider;
import org.ticketing.match.domain.service.SeatGradeProvider;
import org.ticketing.match.domain.service.StadiumProvider;

/**
 * Match 어그리게이트 오케스트레이션 서비스.
 *
 * <h3>두 빈(Two-Bean) 패턴</h3>
 * <p>Feign 호출이 필요한 메서드({@link #createMatch})는 트랜잭션 없이 검증을 수행한 뒤
 * {@link MatchWriteService}에 쓰기를 위임한다.
 *
 * <ul>
 *   <li>Feign 호출 동안 DB 커넥션을 잡지 않으므로 커넥션 풀 고갈 위험 없음.</li>
 *   <li>도메인 변경 + Outbox 저장이 {@link MatchWriteService} 의 단일 {@code @Transactional} 로 원자적으로 처리됨.</li>
 * </ul>
 *
 * <h3>이벤트 발행</h3>
 * <p>모든 Kafka 이벤트는 {@link MatchWriteService} 내부에서 {@code MatchEventPublisher} 를 통해 발행된다.
 * {@code OutboxEventListener} 가 동일 트랜잭션에서 Outbox 를 저장하고,
 * 커밋 후 Kafka 전송까지 처리하므로 이 클래스에서는 이벤트를 직접 다루지 않는다.
 *
 * <h3>APPROVED 분기 (향후 확장)</h3>
 * <p>MatchSeatAvailability 초기화 구현 시 이 클래스의 {@link #changeStatus} 에
 * Feign 호출 분기가 추가될 예정이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchApplicationService {

    private final MatchRepository matchRepository;
    private final MatchWriteService matchWriteService;
    private final ClubProvider clubProvider;
    private final StadiumProvider stadiumProvider;
    private final SeatGradeProvider seatGradeProvider;

    // ──────────────────────────────────────────
    // Match 생성 — Feign 검증 후 커맨드 서비스에 위임
    // ──────────────────────────────────────────

    /**
     * 경기 생성.
     *
     * <p>메서드 레벨에 {@code @Transactional} 을 선언하지 않는다.
     * 클래스 레벨 {@code readOnly = true} 트랜잭션도 이 메서드 실행 중에는
     * JPA 작업이 없으므로 실질적으로 커넥션을 점유하지 않는다.
     * Feign 호출이 끝난 뒤 {@link MatchWriteService#create}가 새 트랜잭션을 열어
     * 모든 쓰기를 원자적으로 처리한다.
     */
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

        // 2. 도메인 객체 생성 (순수 메모리 연산)
        Match match = Match.create(
                command.homeClubId(),
                command.awayClubId(),
                command.stadiumId(),
                command.name(),
                command.matchDatetime(),
                command.ticketOpenAt()
        );

        // 3. 쓰기 위임 — MatchCommandService 의 @Transactional 아래에서 원자적으로 저장
        //    추후 Outbox 저장, 초기 ZonePolicy 추가 등도 이 호출 안에서 같은 트랜잭션으로 처리
        return matchWriteService.create(match);
    }

    // ──────────────────────────────────────────
    // 조회
    // ──────────────────────────────────────────

    public MatchResult findMatch(FindMatchQuery query) {
        Match match = matchRepository.findActiveById(query.matchId())
                .orElseThrow(() -> new MatchNotFoundException(query.matchId()));
        return MatchResult.from(match);
    }

    // ──────────────────────────────────────────
    // 쓰기 위임 — MatchCommandService
    // ──────────────────────────────────────────

    @Transactional
    public MatchResult updateMatch(UpdateMatchCommand command) {
        return matchWriteService.update(command);
    }

    @Transactional
    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        return matchWriteService.changeStatus(command);
    }

    @Transactional
    public void deleteMatch(DeleteMatchCommand command) {
        matchWriteService.delete(command);
    }

    /**
     * ZonePolicy 추가.
     *
     * <p>{@code createMatch} 와 동일하게 메서드 레벨 {@code @Transactional} 을 두지 않는다.
     * SeatGrade 존재 검증을 Feign 으로 수행한 뒤 {@link MatchWriteService#addZonePolicy} 에
     * 쓰기를 위임하므로, Feign 호출 동안 DB 커넥션을 점유하지 않는다.
     */
    public MatchZonePolicyResult addZonePolicy(AddMatchZonePolicyCommand command) {
        if (!seatGradeProvider.existsById(command.seatGradeId())) {
            throw new SeatGradeNotFoundException(command.seatGradeId());
        }
        return matchWriteService.addZonePolicy(command);
    }

    @Transactional
    public MatchZonePolicyResult updateZonePolicy(UpdateMatchZonePolicyCommand command) {
        return matchWriteService.updateZonePolicy(command);
    }

    @Transactional
    public void removeZonePolicy(RemoveMatchZonePolicyCommand command) {
        matchWriteService.removeZonePolicy(command);
    }
}
