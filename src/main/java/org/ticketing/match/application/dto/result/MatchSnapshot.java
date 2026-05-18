package org.ticketing.match.application.dto.result;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.ticketing.match.domain.model.Match;

/**
 * Redis 캐시에 저장되는 Match + ZonePolicy 스냅샷.
 *
 * <p>JPA 엔티티를 캐싱하면 Hibernate 프록시·LazyInitialization 문제가 생기므로,
 * 순수 데이터 record 로 변환해 캐싱한다.
 * ZonePolicy 는 삭제된 것을 제외한 활성 목록만 포함한다.
 */
public record MatchSnapshot(
        UUID matchId,
        List<ZonePolicySnapshot> zonePolicies
) implements Serializable {

    public record ZonePolicySnapshot(
            UUID seatGradeId,
            Long price,
            Long totalSeatCount
    ) implements Serializable {}

    public static MatchSnapshot from(Match match) {
        List<ZonePolicySnapshot> policies = match.getZonePolicies().stream()
                .filter(p -> p.getDeletedAt() == null)
                .map(p -> new ZonePolicySnapshot(
                        p.getSeatGradeId(),
                        p.getPrice(),
                        p.getTotalSeatCount()
                ))
                .toList();
        return new MatchSnapshot(match.getId(), policies);
    }
}
