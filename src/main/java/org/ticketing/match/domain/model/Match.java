package org.ticketing.match.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ticketing.common.domain.BaseEntity;
import org.ticketing.match.domain.exception.InvalidMatchStatusTransitionException;
import org.ticketing.match.domain.exception.MatchNotEditableException;
import org.ticketing.match.domain.exception.MatchZonePolicyNotFoundException;

@Getter
@Entity
@Table(name = "p_match")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AttributeOverrides({
        @AttributeOverride(name = "modifiedAt", column = @Column(name = "updated_at", insertable = false)),
        @AttributeOverride(name = "modifiedBy", column = @Column(name = "updated_by", insertable = false))
})
public class Match extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "home_club_id", nullable = false, columnDefinition = "uuid")
    private UUID homeClubId;

    @Column(name = "away_club_id", nullable = false, columnDefinition = "uuid")
    private UUID awayClubId;

    @Column(name = "stadium_id", nullable = false, columnDefinition = "uuid")
    private UUID stadiumId;

    @Column(name = "name", nullable = false, length = 30)
    private String name;

    @Column(name = "match_datetime", nullable = false)
    private OffsetDateTime matchDatetime;

    @Column(name = "ticket_open_at", nullable = false)
    private OffsetDateTime ticketOpenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MatchStatus status = MatchStatus.DRAFT;

    /**
     * MatchZonePolicy는 Match 어그리게이트 내부 엔티티.
     * 외부에서 직접 접근 불가 — Match의 메서드를 통해서만 조작한다.
     */
    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<MatchZonePolicy> zonePolicies = new ArrayList<>();

    @Builder
    private Match(UUID homeClubId, UUID awayClubId, UUID stadiumId,
                  String name, OffsetDateTime matchDatetime, OffsetDateTime ticketOpenAt) {
        this.homeClubId = homeClubId;
        this.awayClubId = awayClubId;
        this.stadiumId = stadiumId;
        this.name = name;
        this.matchDatetime = matchDatetime;
        this.ticketOpenAt = ticketOpenAt;
        this.status = MatchStatus.DRAFT;
    }

    public static Match create(UUID homeClubId, UUID awayClubId, UUID stadiumId,
                               String name, OffsetDateTime matchDatetime, OffsetDateTime ticketOpenAt) {
        return Match.builder()
                .homeClubId(homeClubId).awayClubId(awayClubId).stadiumId(stadiumId)
                .name(name).matchDatetime(matchDatetime).ticketOpenAt(ticketOpenAt)
                .build();
    }

    // ──────────────────────────────────────────
    // Match 자체 행동
    // ──────────────────────────────────────────

    public void changeStatus(MatchStatus targetStatus) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new InvalidMatchStatusTransitionException(this.status, targetStatus);
        }
        this.status = targetStatus;
    }

    public void update(String name, OffsetDateTime matchDatetime, OffsetDateTime ticketOpenAt) {
        if (!this.status.isEditable()) {
            throw new MatchNotEditableException(this.status);
        }
        this.name = name;
        this.matchDatetime = matchDatetime;
        this.ticketOpenAt = ticketOpenAt;
    }

    public void delete(String deletedBy) {
        super.delete(deletedBy);
    }

    // ──────────────────────────────────────────
    // ZonePolicy 관리 (어그리게이트 루트를 통한 접근)
    // ──────────────────────────────────────────

    public MatchZonePolicy addZonePolicy(UUID seatGradeId, Long price) {
        MatchZonePolicy policy = MatchZonePolicy.create(this, seatGradeId, price);
        this.zonePolicies.add(policy);
        return policy;
    }

    public MatchZonePolicy findZonePolicy(UUID policyId) {
        return zonePolicies.stream()
                .filter(p -> p.getId().equals(policyId) && p.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new MatchZonePolicyNotFoundException(policyId));
    }

    public void updateZonePolicy(UUID policyId, Long price) {
        findZonePolicy(policyId).updatePrice(price);
    }

    public void removeZonePolicy(UUID policyId, String deletedBy) {
        findZonePolicy(policyId).delete(deletedBy);
    }

    public List<MatchZonePolicy> getZonePolicies() {
        return Collections.unmodifiableList(zonePolicies);
    }
}
