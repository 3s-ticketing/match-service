package org.ticketing.match.domain.repository;

import java.util.Map;
import java.util.UUID;

/**
 * 구역별 실시간 잔여 좌석 수를 관리하는 도메인 레포지토리 인터페이스.
 *
 * <p>Redis Hash 를 구현체로 사용한다.
 * <ul>
 *   <li>key   : {@code seat:remaining:{matchId}}</li>
 *   <li>field : seatGradeId (문자열)</li>
 *   <li>value : 잔여 좌석 수</li>
 * </ul>
 */
public interface SeatAvailabilityRepository {

    /**
     * 경기 승인 시 구역별 잔여 좌석 수를 초기화한다.
     *
     * @param matchId         경기 ID
     * @param seatCountByGrade seatGradeId → totalSeatCount 맵
     */
    void initialize(UUID matchId, Map<UUID, Long> seatCountByGrade);

    /**
     * 좌석 예매 시 해당 구역의 잔여 좌석 수를 1 감소시킨다.
     *
     * @param matchId     경기 ID
     * @param seatGradeId 좌석 등급 ID
     */
    void decrement(UUID matchId, UUID seatGradeId);

    /**
     * 예매 취소/해제 시 해당 구역의 잔여 좌석 수를 1 증가시킨다.
     *
     * @param matchId     경기 ID
     * @param seatGradeId 좌석 등급 ID
     */
    void increment(UUID matchId, UUID seatGradeId);

    /**
     * 경기의 모든 구역 잔여 좌석 수를 조회한다.
     *
     * @param matchId 경기 ID
     * @return seatGradeId → 잔여 좌석 수 맵 (캐시 미존재 시 빈 맵)
     */
    Map<UUID, Long> findAll(UUID matchId);
}
