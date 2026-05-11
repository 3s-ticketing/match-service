package org.ticketing.match.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "seat-service", contextId = "seatGradeClient")
public interface SeatGradeClient {

    @GetMapping("/internal/seat-grades/{seatGradeId}/exists")
    boolean existsById(@PathVariable UUID seatGradeId);

    @GetMapping("/internal/seat-grades/{seatGradeId}/count")
    long countBySeatGradeId(@PathVariable UUID seatGradeId);
}
