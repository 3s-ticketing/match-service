package org.ticketing.match.application.dto.result;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.ticketing.match.domain.model.Match;
import org.ticketing.match.domain.model.MatchStatus;

public record MatchResult(
        UUID id,
        UUID homeClubId,
        UUID awayClubId,
        UUID stadiumId,
        String name,
        OffsetDateTime matchDatetime,
        OffsetDateTime ticketOpenAt,
        MatchStatus status,
        List<MatchZonePolicyResult> zonePolicies,
        LocalDateTime createdAt,
        String createdBy
) {

    public static MatchResult from(Match match) {
        List<MatchZonePolicyResult> policies = match.getZonePolicies().stream()
                .filter(p -> p.getDeletedAt() == null)
                .map(MatchZonePolicyResult::from)
                .toList();

        return new MatchResult(
                match.getId(),
                match.getHomeClubId(),
                match.getAwayClubId(),
                match.getStadiumId(),
                match.getName(),
                match.getMatchDatetime(),
                match.getTicketOpenAt(),
                match.getStatus(),
                policies,
                match.getCreatedAt(),
                match.getCreatedBy()
        );
    }
}
