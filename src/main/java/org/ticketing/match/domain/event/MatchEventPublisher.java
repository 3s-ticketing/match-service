package org.ticketing.match.domain.event;

import org.ticketing.match.domain.event.payload.MatchCanceledEvent;
import org.ticketing.match.domain.event.payload.MatchCreatedEvent;
import org.ticketing.match.domain.event.payload.MatchStatusChangedEvent;
import org.ticketing.match.domain.event.payload.MatchZonePolicyCreatedEvent;

public interface MatchEventPublisher {

    void publishMatchCreated(MatchCreatedEvent event);

    void publishMatchCanceled(MatchCanceledEvent event);

    void publishMatchStatusChanged(MatchStatusChangedEvent event);

    void publishMatchZonePolicyCreated(MatchZonePolicyCreatedEvent event);
}
