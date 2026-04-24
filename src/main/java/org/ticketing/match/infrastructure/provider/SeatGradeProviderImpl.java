package org.ticketing.match.infrastructure.provider;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ticketing.match.domain.service.SeatGradeProvider;
import org.ticketing.match.infrastructure.client.SeatGradeClient;

@Component
@RequiredArgsConstructor
public class SeatGradeProviderImpl implements SeatGradeProvider {

    private final SeatGradeClient seatGradeClient;

    @Override
    public boolean existsById(UUID seatGradeId) {
        return seatGradeClient.existsById(seatGradeId);
    }
}
