package org.ticketing.match.domain.service;

import java.util.UUID;

/** SeatGrade 서비스로부터 좌석 등급 정보를 조회하는 도메인 서비스 인터페이스. */
public interface SeatGradeProvider {

    boolean existsById(UUID seatGradeId);
}
