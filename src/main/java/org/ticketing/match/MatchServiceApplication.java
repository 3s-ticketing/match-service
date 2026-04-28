package org.ticketing.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @EntityScan / @EnableJpaRepositories 는 common-module 의 JPAConfig 가
 * @EntityScan("org.ticketing") / @EnableJpaRepositories("org.ticketing") 으로 이미 적용하므로 생략한다.
 * Feign, Kafka, JpaAuditing 등 공통 설정도 AppCtx AutoConfiguration 을 통해 자동으로 활성화된다.
 */
@SpringBootApplication
public class MatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchServiceApplication.class, args);
    }
}
