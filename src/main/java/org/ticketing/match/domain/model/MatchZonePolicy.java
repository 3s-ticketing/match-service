package org.ticketing.match.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;

/**
 * Match 어그리게이트 내부 엔티티.
 * 독립적인 Repository 없이 Match를 통해서만 접근한다.
 */
@Getter
@Entity
@Table(name = "p_matchzonepolicy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchZonePolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Column(name = "seat_grade_id", nullable = false, columnDefinition = "uuid")
    private UUID seatGradeId;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "is_open", nullable = false)
    private Boolean isOpen;

    static MatchZonePolicy create(Match match, UUID seatGradeId, Long price) {
        MatchZonePolicy policy = new MatchZonePolicy();
        policy.match = match;
        policy.seatGradeId = seatGradeId;
        policy.price = price;
        policy.isOpen = false;
        return policy;
    }

    void updatePrice(Long price) {
        this.price = price;
    }

    void open() {
        this.isOpen = true;
    }

    void close() {
        this.isOpen = false;
    }

    @Override
    protected void delete(String deletedBy) {
        super.delete(deletedBy);
    }
}
