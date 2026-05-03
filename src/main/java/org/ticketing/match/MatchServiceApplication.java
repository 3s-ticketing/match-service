package org.ticketing.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;

/**
 * @EntityScan / @EnableJpaRepositories 는 common-module 의 JPAConfig 가
 * @EntityScan("org.ticketing") / @EnableJpaRepositories("org.ticketing") 으로 이미 적용하므로 생략한다.
 * Feign, Kafka, JpaAuditing 등 공통 설정도 AppCtx AutoConfiguration 을 통해 자동으로 활성화된다.
 *
 * <p>Spring Boot 의 {@link JpaRepositoriesAutoConfiguration} 은 common-module 의 자동설정과
 * 처리 순서가 보장되지 않아 동일 Repository 를 두 번 등록하는 충돌이 발생한다.
 * common-module 의 {@code @EnableJpaRepositories} 가 단일 진입점이므로 Spring Boot 자동설정은 제외한다.
 */
@SpringBootApplication(exclude = JpaRepositoriesAutoConfiguration.class)
public class MatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchServiceApplication.class, args);
    }
}
