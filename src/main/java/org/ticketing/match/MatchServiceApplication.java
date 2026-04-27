package org.ticketing.match;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * @EntityScan: common-module 의 Outbox 엔티티(org.ticketing.common.domain) 포함
 * @EnableJpaRepositories: common-module 의 OutboxRepository 포함
 */
@SpringBootApplication
@EntityScan(basePackages = {
        "org.ticketing.match",
        "org.ticketing.common.domain"
})
@EnableJpaRepositories(basePackages = {
        "org.ticketing.match",
        "org.ticketing.common.domain"
})
public class MatchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchServiceApplication.class, args);
    }
}
