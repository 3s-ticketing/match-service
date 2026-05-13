package org.ticketing.match.presentation.dto.response.internal;

import java.util.UUID;

/**
 * 내부 서비스 간 통신용 좌석 등급 가격 응답 DTO.
 *
 * <p>reservation-service 가 좌석 hold/confirm 시 가격 메타데이터를 조회하기 위해 사용한다.
 * 공통 모듈의 {@code @RestControllerAdvice} 가 {@code {success, data, traceId}} 형태로 래핑한다.
 */
public record SeatGradePriceResponseDto(
        UUID seatGradeId,
        Long price
) {
    public static SeatGradePriceResponseDto of(UUID seatGradeId, Long price) {
        return new SeatGradePriceResponseDto(seatGradeId, price);
    }
}
