package org.ticketing.match.presentation.controller;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.match.application.service.MatchApplicationService;
import org.ticketing.match.presentation.dto.response.internal.SeatGradePriceResponseDto;

/**
 * 서비스 간 내부 통신 전용 컨트롤러.
 *
 * <h3>접근 제어</h3>
 * <p>{@code /internal/**} 경로는 API Gateway 에서 외부 노출이 차단된다.
 * 서비스 내부 네트워크에서만 호출 가능하며, 별도 JWT 검증 없이 동작한다
 * (게이트웨이가 외부 요청을 이미 필터링하므로).
 *
 * <h3>응답 형식</h3>
 * <p>공통 모듈의 {@code GlobalResponseAdvice} 가 자동으로
 * {@code {success, message, data, traceId}} 형태로 래핑한다.
 * 호출 측(reservation-service)의 Feign DTO 는 이 래퍼 구조를 포함해야 한다.
 */
@RestController
@RequestMapping("/internal/matches")
@RequiredArgsConstructor
public class InternalMatchController {

    private final MatchApplicationService matchApplicationService;

    /**
     * 경기-좌석등급 조합의 가격 조회.
     *
     * <p>reservation-service 가 좌석 confirm 시 가격 스냅샷을 저장하기 위해 호출한다.
     *
     * @param matchId     경기 ID
     * @param seatGradeId 좌석 등급 ID
     * @return 해당 경기의 좌석 등급 가격 정보
     */
    @GetMapping("/{matchId}/seat-grades/{seatGradeId}")
    public SeatGradePriceResponseDto getSeatGradePrice(
            @PathVariable UUID matchId,
            @PathVariable UUID seatGradeId
    ) {
        MatchApplicationService.SeatGradePrice result =
                matchApplicationService.getSeatGradePrice(matchId, seatGradeId);
        return SeatGradePriceResponseDto.of(result.seatGradeId(), result.price());
    }
}
