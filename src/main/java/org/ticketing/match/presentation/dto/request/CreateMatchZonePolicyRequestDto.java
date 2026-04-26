package org.ticketing.match.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.ticketing.match.application.dto.command.AddMatchZonePolicyCommand;

public record CreateMatchZonePolicyRequestDto(

        @NotNull(message = "좌석 등급 ID는 필수입니다.")
        UUID seatGradeId,

        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 양수여야 합니다.")
        Long price
) {

    public AddMatchZonePolicyCommand toCommand(UUID matchId) {
        return new AddMatchZonePolicyCommand(matchId, seatGradeId, price);
    }
}
