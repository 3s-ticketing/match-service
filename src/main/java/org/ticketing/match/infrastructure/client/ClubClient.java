package org.ticketing.match.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.match.infrastructure.client.dto.CommonResponse;

@FeignClient(name = "club-service")
public interface ClubClient {

    @GetMapping("/internal/clubs/{clubId}/exists")
    CommonResponse<Boolean> existsClub(@PathVariable("clubId") UUID clubId);

    @GetMapping("/internal/stadiums/{stadiumId}/exists")
    CommonResponse<Boolean> existsStadium(@PathVariable("stadiumId") UUID stadiumId);
}
