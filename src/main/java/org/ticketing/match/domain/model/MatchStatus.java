package org.ticketing.match.domain.model;

public enum MatchStatus {

    /** CLUB_ADMIN 작성 중 → PENDING_APPROVAL 전이 가능 */
    DRAFT,

    /** ADMIN 승인 대기 → APPROVED, DRAFT 전이 가능 */
    PENDING_APPROVAL,

    /** 승인 완료 → OPEN 전이 가능 */
    APPROVED,

    /** 예매 가능 (ticket_open_at 도달 후) → CLOSED, CANCELED 전이 가능 */
    OPEN,

    /** 예매 종료 */
    CLOSED,

    /** 경기 취소 (CLOSED 를 제외한 모든 상태에서 전이 가능) */
    CANCELED;

    public boolean canTransitionTo(MatchStatus target) {
        return switch (this) {
            case DRAFT            -> target == PENDING_APPROVAL || target == CANCELED;
            case PENDING_APPROVAL -> target == APPROVED || target == DRAFT || target == CANCELED;
            case APPROVED         -> target == OPEN || target == CANCELED;
            case OPEN             -> target == CLOSED || target == CANCELED;
            case CLOSED, CANCELED -> false;
        };
    }

    /** APPROVED 이전(DRAFT / PENDING_APPROVAL) 상태에서만 경기 정보 수정을 허용한다. */
    public boolean isEditable() {
        return this == DRAFT || this == PENDING_APPROVAL;
    }
}
