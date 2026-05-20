package org.ticketing.match.domain.service;

import java.util.UUID;

/** Club 서비스로부터 클럽 정보를 조회하는 도메인 서비스 인터페이스. */
public interface ClubProvider {

    boolean existsById(UUID clubId);
}
