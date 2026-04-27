package org.ticketing.match.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.ticketing.common.domain.OutboxRepository;
import org.ticketing.common.event.Events;
import org.ticketing.common.event.OutboxEventListener;
import org.ticketing.common.event.scheduler.OutboxRelayScheduler;

/**
 * common-module 의 Outbox 인프라를 match-service 에 등록한다.
 *
 * <p>흐름:
 * Events.trigger() → ApplicationEvent(OutboxEvent) →
 * OutboxEventListener.recordOutbox() [같은 트랜잭션, DB 저장] →
 * OutboxEventListener.publish() [AFTER_COMMIT, Kafka 발행] →
 * (실패 시) OutboxRelayScheduler 재시도 → 3회 초과 시 DLT 격리
 *
 * <p>OutboxRepository 는 common-module 의 JPA 인터페이스이므로
 * match-service 의 @EntityScan / @EnableJpaRepositories 에
 * common-module 패키지가 포함되어 있어야 한다.
 */
@Configuration
@EnableScheduling
public class OutboxConfig {

    /**
     * Events 는 static 필드를 @Autowired init() 으로 초기화하는 방식이므로
     * Bean 으로 등록해야 Spring 이 init() 을 자동 주입한다.
     */
    @Bean
    public Events events() {
        return new Events();
    }

    @Bean
    public OutboxEventListener outboxEventListener(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        return new OutboxEventListener(outboxRepository, kafkaTemplate, objectMapper);
    }

    @Bean
    public OutboxRelayScheduler outboxRelayScheduler(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate) {
        return new OutboxRelayScheduler(outboxRepository, kafkaTemplate);
    }
}
