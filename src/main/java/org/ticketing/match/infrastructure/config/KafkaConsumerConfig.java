package org.ticketing.match.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 컨슈머 컨테이너 팩토리 설정.
 *
 * <h3>신뢰성 정책</h3>
 * <ul>
 *   <li><b>AckMode.RECORD</b>: 레코드 처리 완료 후 오프셋을 수동 커밋.
 *       auto-commit(=true) 이면 처리 실패 시에도 오프셋이 커밋되어 메시지 유실.</li>
 *   <li><b>재시도 3회</b>: 1초 간격 고정 백오프(transient Redis 오류 등 일시적 장애 대응).</li>
 *   <li><b>Dead Letter Topic</b>: 3회 재시도 후에도 실패하면 {@code {topic}.DLT} 로 이동.
 *       DLT 파티션은 원본과 동일하게 유지하여 순서 추적 용이.</li>
 *   <li><b>JsonProcessingException 즉시 DLT</b>: 역직렬화 오류는 재시도해도 해결되지 않으므로
 *       즉시 DLT 로 보내 컨슈머 lag 증가를 방지.</li>
 * </ul>
 *
 * <p>{@code ConsumerFactory} 와 {@code KafkaTemplate} 은 Spring Boot 자동 구성에서 주입받으며,
 * {@code application.yaml} 의 {@code spring.kafka.consumer.*} 설정이 함께 적용된다.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /**
     * 일시적 장애(Redis 지연 등)를 고려한 재시도 횟수.
     * 1초 × 3회 → 최대 3초 후 DLT 전송.
     */
    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final long MAX_RETRY_ATTEMPTS = 3L;

    /**
     * Kafka 컨슈머 리스너 팩토리.
     *
     * <p>Spring Boot 자동 구성의 기본 {@code kafkaListenerContainerFactory} 빈을 대체한다.
     * {@code ConsumerFactory} 는 {@code spring.kafka.consumer.*} 설정을 기반으로
     * Spring Boot 가 자동 구성한 빈을 그대로 사용한다.
     *
     * @param consumerFactory Spring Boot 자동 구성 ConsumerFactory
     * @param kafkaTemplate   DLT 발행용 KafkaTemplate
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 레코드 단위 수동 커밋 — 처리 완료 후 오프셋 커밋하여 메시지 유실 방지
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // DLT 복구 전략: 원본 토픽명 + ".DLT", 동일 파티션으로 전송
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error(
                            "[KafkaConsumer] DLT 전송 — topic={}.DLT, partition={}, offset={}, cause={}",
                            record.topic(), record.partition(), record.offset(),
                            ex.getMessage()
                    );
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        // 3회 재시도 (1초 고정 간격) 후 DLT
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_ATTEMPTS)
        );

        // 역직렬화 오류는 재시도로 해결 불가 → 즉시 DLT 전송
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
