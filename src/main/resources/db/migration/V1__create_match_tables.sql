-- ============================================================
-- match-service 초기 스키마
-- 어그리게이트: Match (루트) + MatchZonePolicy (자식 엔티티)
-- ============================================================

CREATE SCHEMA IF NOT EXISTS match;

-- ── p_match (경기) ────────────────────────────────────────────
-- 어그리게이트 루트: Match
-- @AttributeOverrides 로 modified_at/by → updated_at/by 매핑.
CREATE TABLE match.p_match (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    home_club_id    UUID         NOT NULL,
    away_club_id    UUID         NOT NULL,
    stadium_id      UUID         NOT NULL,
    name            VARCHAR(30)  NOT NULL,
    match_datetime  TIMESTAMPTZ  NOT NULL,
    ticket_open_at  TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT', -- DRAFT / OPEN / CLOSED / CANCELED 등
    -- BaseEntity audit (Match 는 @AttributeOverride → updated_*)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    updated_at      TIMESTAMP,
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255)
);

CREATE INDEX idx_match_status         ON match.p_match (status)         WHERE deleted_at IS NULL;
CREATE INDEX idx_match_match_datetime ON match.p_match (match_datetime) WHERE deleted_at IS NULL;


-- ── p_matchzonepolicy (경기별 좌석 등급 정책) ─────────────────
-- Match 어그리게이트의 자식. 루트(match_id)를 통해서만 접근된다.
-- @AttributeOverride 없음 → BaseEntity 의 modified_* 그대로.
--
-- UNIQUE (match_id, seat_grade_id):
--   1차 가드: Match.addZonePolicy() 도메인 가드 (인메모리 중복 검사)
--   2차 가드: 동시 요청이 1차 가드를 동시에 통과한 경우 DB 에서 차단
--   soft delete 된 레코드도 기록 보존 목적으로 유지되며 재사용 X.
CREATE TABLE match.p_matchzonepolicy (
    id              UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    match_id        UUID         NOT NULL REFERENCES match.p_match (id),
    seat_grade_id   UUID         NOT NULL,
    price           BIGINT       NOT NULL,
    is_open         BOOLEAN      NOT NULL DEFAULT FALSE,
    -- BaseEntity audit (override 없음)
    created_at      TIMESTAMP    NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    modified_at     TIMESTAMP,
    modified_by     VARCHAR(255),
    deleted_at      TIMESTAMP,
    deleted_by      VARCHAR(255),
    CONSTRAINT uq_matchzonepolicy_match_seatgrade UNIQUE (match_id, seat_grade_id)
);

CREATE INDEX idx_matchzonepolicy_match_id ON match.p_matchzonepolicy (match_id) WHERE deleted_at IS NULL;
