package org.ticketing.match.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.ticketing.match.infrastructure.client.dto.CommonResponse;

@FeignClient(name = "seat-service", contextId = "seatGradeClient")
public interface SeatGradeClient {

    @GetMapping("/internal/seat-grades/{seatGradeId}/exists")
    CommonResponse<Boolean> existsById(@PathVariable("seatGradeId") UUID seatGradeId);
}
