package org.ticketing.match.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ticketing.match.application.dto.result.MatchZonePolicyResult;

public record MatchZonePolicyResponseDto(
        UUID id,
        UUID matchId,
        UUID seatGradeId,
        Long price,
        Boolean isOpen,
        LocalDateTime createdAt,
        String createdBy
) {

    public static MatchZonePolicyResponseDto from(MatchZonePolicyResult result) {
        return new MatchZonePolicyResponseDto(
                result.id(), result.matchId(), result.seatGradeId(),
                result.price(), result.isOpen(), result.createdAt(), result.createdBy()
        );
    }
}
