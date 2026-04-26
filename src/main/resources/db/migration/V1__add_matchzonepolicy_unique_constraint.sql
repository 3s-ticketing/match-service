-- 동일 Match 내에서 같은 seatGradeId 를 가진 ZonePolicy 가 중복 생성되지 않도록
-- UNIQUE 제약을 추가한다.
--
-- soft delete 된 레코드는 기록 보존 목적으로만 유지되며 재사용하지 않는다.
-- 따라서 삭제 여부와 무관하게 (match_id, seat_grade_id) 조합은 유일해야 하므로
-- Partial Index 가 아닌 일반 UNIQUE 제약을 사용한다.
--
-- 1차 방어: Match.addZonePolicy() 도메인 가드 (인메모리 중복 검사)
-- 2차 방어: 아래 제약 (동시 요청이 1차 가드를 동시에 통과한 경우 DB 에서 차단)
ALTER TABLE p_matchzonepolicy
    ADD CONSTRAINT uq_matchzonepolicy_match_seatgrade UNIQUE (match_id, seat_grade_id);
