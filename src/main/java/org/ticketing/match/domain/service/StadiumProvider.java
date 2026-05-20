package org.ticketing.match.domain.service;

import java.util.UUID;

/** Stadium 서비스로부터 경기장 정보를 조회하는 도메인 서비스 인터페이스. */
public interface StadiumProvider {

    boolean existsById(UUID stadiumId);
}
