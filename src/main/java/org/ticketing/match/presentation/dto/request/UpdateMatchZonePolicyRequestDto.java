package org.ticketing.match.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.ticketing.match.application.dto.command.UpdateMatchZonePolicyCommand;

public record UpdateMatchZonePolicyRequestDto(

        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 양수여야 합니다.")
        Long price
) {

    public UpdateMatchZonePolicyCommand toCommand(UUID matchId, UUID policyId) {
        return new UpdateMatchZonePolicyCommand(matchId, policyId, price);
    }
}
