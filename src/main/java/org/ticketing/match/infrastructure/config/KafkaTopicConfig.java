package org.ticketing.match.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 토픽을 애플리케이션 기동 시 자동으로 생성/검증한다.
 * 이미 존재하는 토픽은 영향받지 않으며, 존재하지 않을 때만 생성된다.
 *
 * <p>토픽명은 프로퍼티로 관리되며 기본값이 제공된다.
 * <p>partitions: 컨슈머 병렬 처리 단위 (기본 3)
 * <p>replicas:   복제본 수 — 로컬/개발은 1, 운영은 브로커 수 이상 권장
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${kafka.topic.partitions:3}")
    private int partitions;

    @Value("${kafka.topic.replicas:1}")
    private short replicas;

    @Value("${topics.match.created:match.created}")
    private String matchCreatedTopic;

    @Value("${topics.match.canceled:match.canceled}")
    private String matchCanceledTopic;

    @Value("${topics.match.status-changed:match.status-changed}")
    private String matchStatusChangedTopic;

    @Value("${topics.match.zone-policy.created:match.zone-policy.created}")
    private String matchZonePolicyCreatedTopic;

    @Bean
    public NewTopic matchCreatedTopic() {
        return TopicBuilder.name(matchCreatedTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic matchCanceledTopic() {
        return TopicBuilder.name(matchCanceledTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic matchStatusChangedTopic() {
        return TopicBuilder.name(matchStatusChangedTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }

    @Bean
    public NewTopic matchZonePolicyCreatedTopic() {
        return TopicBuilder.name(matchZonePolicyCreatedTopic)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
