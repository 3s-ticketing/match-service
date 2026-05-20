package org.ticketing.match.infrastructure.provider;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketing.match.domain.service.ClubProvider;
import org.ticketing.match.infrastructure.client.ClubClient;

@Component
@RequiredArgsConstructor
public class ClubProviderImpl implements ClubProvider {

    private final ClubClient clubClient;

    @Override
    public boolean existsById(UUID clubId) {
        return Boolean.TRUE.equals(clubClient.existsClub(clubId).data());
    }
}
