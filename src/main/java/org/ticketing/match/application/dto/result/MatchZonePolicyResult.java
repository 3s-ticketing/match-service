package org.ticketing.match.application.dto.result;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ticketing.match.domain.model.MatchZonePolicy;

public record MatchZonePolicyResult(
        UUID id,
        UUID matchId,
        UUID seatGradeId,
        Long price,
        Boolean isOpen,
        LocalDateTime createdAt,
        String createdBy
) {

    public static MatchZonePolicyResult from(MatchZonePolicy policy) {
        return new MatchZonePolicyResult(
                policy.getId(),
                policy.getMatch().getId(),
                policy.getSeatGradeId(),
                policy.getPrice(),
                policy.getIsOpen(),
                policy.getCreatedAt(),
                policy.getCreatedBy()
        );
    }
}
