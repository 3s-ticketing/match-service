package org.ticketing.match.infrastructure.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.ticketing.config.security.LoginFilter;

/**
 * match-service 보안 설정.
 *
 * <h3>인증 모델</h3>
 * <p>OAuth2 Resource Server 로 동작한다. Gateway 단에서 1차 검증된 Keycloak JWT 가
 * Authorization 헤더로 그대로 전달되며, 본 서비스에서도 재검증 후 SecurityContext 에
 * {@link org.springframework.security.oauth2.jwt.Jwt} 로 바인딩한다.
 *
 * <h3>LoginFilter 자동 등록 비활성화</h3>
 * <p>common-module 의 {@code AppCtx} 가 {@link LoginFilter} 빈을 등록하면 Spring Boot 가
 * 이를 서블릿 필터로 자동 등록한다. LoginFilter 는 {@code SecurityContextHolder.clearContext()}
 * 를 호출하므로 {@link org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationFilter}
 * 가 세팅한 {@code JwtAuthenticationToken} 을 날려버린다.
 * {@link FilterRegistrationBean#setEnabled(boolean) setEnabled(false)} 로 자동 등록만 막는다.
 *
 * <h3>경로별 권한</h3>
 * <ul>
 *   <li>{@code /actuator/health} — Eureka health URL, 무인증</li>
 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — API 문서, 무인증</li>
 *   <li>{@code /internal/**} — 서비스 간 호출, 게이트웨이가 외부 노출 차단</li>
 *   <li>그 외 — 인증 필요 (JWT 검증)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    /**
     * LoginFilter 서블릿 자동 등록 비활성화.
     * BearerTokenAuthenticationFilter 가 세팅한 JwtAuthenticationToken 이 유지되도록 한다.
     */
    @Bean
    public FilterRegistrationBean<LoginFilter> disableLoginFilterAutoRegistration(LoginFilter loginFilter) {
        FilterRegistrationBean<LoginFilter> registration = new FilterRegistrationBean<>(loginFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/internal/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
