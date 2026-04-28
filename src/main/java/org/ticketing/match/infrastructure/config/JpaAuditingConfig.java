package org.ticketing.match.infrastructure.config;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.ticketing.match.infrastructure.security.SecurityContextProvider;

/**
 * JPA Auditing 설정.
 * {@code @EnableJpaAuditing} 은 common-module 의 JPAConfig 에서 이미 선언되므로 여기서는 생략한다.
 * (중복 선언 시 AuditingEntityListener 관련 빈 충돌 가능)
 *
 * <p>AuditorAware 만 별도로 등록한다.
 * Gateway 를 통해 넘어온 Security Context 에서 username 을 추출하며,
 * 인증 정보가 없으면 "system" 을 기본값으로 사용한다.
 */
@Configuration
@RequiredArgsConstructor
public class JpaAuditingConfig {

    private final SecurityContextProvider securityContextProvider;

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.of(securityContextProvider.getCurrentUsername());
    }
}
