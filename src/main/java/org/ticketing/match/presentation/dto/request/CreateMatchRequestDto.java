package org.ticketing.match.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.ticketing.match.application.dto.command.CreateMatchCommand;

public record CreateMatchRequestDto(

        @NotNull(message = "홈 클럽 ID는 필수입니다.")
        UUID homeClubId,

        @NotNull(message = "어웨이 클럽 ID는 필수입니다.")
        UUID awayClubId,

        @NotNull(message = "경기장 ID는 필수입니다.")
        UUID stadiumId,

        @NotBlank(message = "경기 이름은 필수입니다.")
        @Size(max = 30, message = "경기 이름은 최대 30자입니다.")
        String name,

        @NotNull(message = "경기 일시는 필수입니다.")
        OffsetDateTime matchDatetime,

        @NotNull(message = "티켓 오픈 시간은 필수입니다.")
        OffsetDateTime ticketOpenAt
) {

    public CreateMatchCommand toCommand() {
        return new CreateMatchCommand(homeClubId, awayClubId, stadiumId, name, matchDatetime, ticketOpenAt);
    }
}
