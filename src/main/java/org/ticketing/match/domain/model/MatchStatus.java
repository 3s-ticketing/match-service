package org.ticketing.match.domain.model;

public enum MatchStatus {

    /** CLUB_ADMIN 작성 중 → PENDING_APPROVAL 전이 가능 */
    DRAFT,

    /** ADMIN 승인 대기 → APPROVED, DRAFT 전이 가능 */
    PENDING_APPROVAL,

    /** 승인 완료 → ticket_open_at 도달 시 OPEN 전이 */
    APPROVED,

    /** 예매 가능 (ticket_open_at 도달 후) → CLOSED 전이 가능 */
    OPEN,

    /** 예매 종료 */
    CLOSED;

    public boolean canTransitionTo(MatchStatus target) {
        return switch (this) {
            case DRAFT            -> target == PENDING_APPROVAL;
            case PENDING_APPROVAL -> target == APPROVED || target == DRAFT;
            case APPROVED         -> target == OPEN;
            case OPEN             -> target == CLOSED;
            case CLOSED           -> false;
        };
    }

    /** APPROVED 이전(DRAFT / PENDING_APPROVAL) 상태에서만 경기 정보 수정을 허용한다. */
    public boolean isEditable() {
        return this == DRAFT || this == PENDING_APPROVAL;
    }
}
