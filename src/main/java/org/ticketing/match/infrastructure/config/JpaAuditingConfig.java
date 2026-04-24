package org.ticketing.match.infrastructure.config;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.ticketing.match.infrastructure.security.SecurityContextProvider;

@Configuration
@EnableJpaAuditing
@RequiredArgsConstructor
public class JpaAuditingConfig {

    private final SecurityContextProvider securityContextProvider;

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(securityContextProvider.getCurrentUsername());
    }
}
