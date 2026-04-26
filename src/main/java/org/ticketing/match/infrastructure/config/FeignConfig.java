package org.ticketing.match.infrastructure.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "org.ticketing.match.infrastructure.client")
public class FeignConfig {
}
