package org.ticketing.match.infrastructure.provider;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketing.match.domain.service.StadiumProvider;
import org.ticketing.match.infrastructure.client.StadiumClient;

@Component
@RequiredArgsConstructor
public class StadiumProviderImpl implements StadiumProvider {

    private final StadiumClient stadiumClient;

    @Override
    public boolean existsById(UUID stadiumId) {
        return stadiumClient.existsById(stadiumId);
    }
}
