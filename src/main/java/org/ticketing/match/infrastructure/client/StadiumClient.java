package org.ticketing.match.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "stadium-service")
public interface StadiumClient {

    @GetMapping("/api/stadiums/{stadiumId}/exists")
    boolean existsById(@PathVariable UUID stadiumId);
}
