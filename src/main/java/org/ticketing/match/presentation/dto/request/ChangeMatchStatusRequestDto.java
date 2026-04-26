package org.ticketing.match.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.ticketing.match.application.dto.command.ChangeMatchStatusCommand;
import org.ticketing.match.domain.model.MatchStatus;

public record ChangeMatchStatusRequestDto(

        @NotNull(message = "변경할 상태는 필수입니다.")
        MatchStatus targetStatus
) {

    public ChangeMatchStatusCommand toCommand(UUID matchId) {
        return new ChangeMatchStatusCommand(matchId, targetStatus);
    }
}
