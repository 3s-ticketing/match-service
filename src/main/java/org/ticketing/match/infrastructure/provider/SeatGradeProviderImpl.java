package org.ticketing.match.infrastructure.provider;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ticketing.match.domain.exception.SeatGradeNotFoundException;
import org.ticketing.match.domain.service.SeatGradeProvider;
import org.ticketing.match.infrastructure.client.SeatGradeClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeatGradeProviderImpl implements SeatGradeProvider {

    private final SeatGradeClient seatGradeClient;

    @Override
    public boolean existsById(UUID seatGradeId) {
        return Boolean.TRUE.equals(seatGradeClient.existsById(seatGradeId).data());
    }

    /**
     * 좌석 등급의 총 좌석 수를 조회한다.
     *
     * <p>응답이 null 이거나 0 이하이면 seat-service 오류 또는 잘못된 등급으로 판단하여 예외를 던진다.
     * 0 을 그대로 반환하면 APPROVED 시 Redis 잔여 좌석이 0 으로 초기화되어
     * 해당 구역이 즉시 매진 처리되는 심각한 부작용이 발생한다.
     */
    @Override
    public long countBySeatGradeId(UUID seatGradeId) {
        Long count = seatGradeClient.countBySeatGradeId(seatGradeId).data();
        if (count == null || count <= 0) {
            log.error("[SeatGradeProvider] seatGradeId={} 좌석 수 조회 실패 — count={}. "
                    + "seat-service 응답 이상 또는 잘못된 seatGradeId.", seatGradeId, count);
            throw new SeatGradeNotFoundException(seatGradeId);
        }
        return count;
    }
}
