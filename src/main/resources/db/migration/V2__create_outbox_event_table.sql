-- common-module 의 Outbox 엔티티(org.ticketing.common.domain.Outbox)에 대응하는 테이블.
-- eventType 컬럼이 Kafka 토픽으로 직접 사용된다.
-- OutboxRelayScheduler 가 PENDING/FAILED 레코드를 재시도하며,
-- retry_count 가 3 초과 시 Dead Letter Topic(eventType + ".DLT")으로 격리된다.

CREATE TABLE p_outbox
(
    message_id     UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    correlation_id VARCHAR(64)  NOT NULL UNIQUE,    -- 멱등성 키 (중복 이벤트 방지)
    domain_type    VARCHAR(50)  NOT NULL,            -- ex) 'MATCH'
    domain_id      VARCHAR(50)  NOT NULL,            -- ex) matchId (Kafka message key)
    event_type     VARCHAR(100) NOT NULL,            -- Kafka 토픽명, ex) 'match.canceled'
    payload        TEXT         NOT NULL,            -- JSON 직렬화된 이벤트 본문
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
    retry_count    INT          NOT NULL DEFAULT 0,
    -- BaseEntity 감사 필드
    created_at     TIMESTAMP,
    modified_at    TIMESTAMP,
    deleted_at     TIMESTAMP,
    created_by     VARCHAR(255),
    modified_by    VARCHAR(255),
    deleted_by     VARCHAR(255)
);

-- 스케줄러가 PENDING/FAILED 이벤트를 빠르게 조회하기 위한 인덱스
CREATE INDEX idx_outbox_status ON p_outbox (status);
