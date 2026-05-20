package org.ticketing.match.presentation.dto.response;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.ticketing.match.application.dto.result.MatchResult;
import org.ticketing.match.domain.model.MatchStatus;

public record MatchResponseDto(
        UUID id,
        UUID homeClubId,
        UUID awayClubId,
        UUID stadiumId,
        String name,
        OffsetDateTime matchDatetime,
        OffsetDateTime ticketOpenAt,
        MatchStatus status,
        List<MatchZonePolicyResponseDto> zonePolicies,
        LocalDateTime createdAt,
        String createdBy
) {

    public static MatchResponseDto from(MatchResult result) {
        return new MatchResponseDto(
                result.id(), result.homeClubId(), result.awayClubId(), result.stadiumId(),
                result.name(), result.matchDatetime(), result.ticketOpenAt(), result.status(),
                result.zonePolicies().stream().map(MatchZonePolicyResponseDto::from).toList(),
                result.createdAt(), result.createdBy()
        );
    }
}
