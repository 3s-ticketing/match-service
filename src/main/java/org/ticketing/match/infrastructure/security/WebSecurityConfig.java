package org.ticketing.match.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.ticketing.config.security.CustomAccessDeniedHandler;
import org.ticketing.config.security.CustomAuthenticationEntryPoint;
import org.ticketing.config.security.LoginFilter;

/**
 * match-service 전용 Spring Security 설정.
 *
 * <p>common-module 의 {@code AppCtx} 는 {@code SecurityConfig} 를 {@code @Bean} 인스턴스로만
 * 등록하므로, {@code @EnableWebSecurity} 의 {@code @Import} 체인이 처리되지 않아
 * Spring Boot 기본 Basic 인증이 활성화되는 문제가 발생한다.
 * 이 클래스를 통해 {@code @EnableWebSecurity} 를 명시적으로 활성화하고
 * {@code SecurityFilterChain} 을 직접 등록한다.
 *
 * <p>인증은 Gateway 가 JWT 검증 후 주입하는 {@code X-User-Id} / {@code X-User-Email}
 * 헤더를 {@link LoginFilter} 가 읽어 SecurityContext 에 설정하는 방식으로 동작한다.
 * 헤더가 없는 직접 접근(Gateway 우회)은 익명(anonymous) 상태로 통과하며,
 * {@code anyRequest().permitAll()} 에 의해 모든 엔드포인트가 허용된다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final LoginFilter loginFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(loginFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                )
                .exceptionHandling(c -> {
                    c.authenticationEntryPoint(authenticationEntryPoint);
                    c.accessDeniedHandler(accessDeniedHandler);
                });

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/favicon.ico", "/error");
    }
}
