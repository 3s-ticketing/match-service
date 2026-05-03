package org.ticketing.match.domain.event;

import org.ticketing.match.domain.event.payload.MatchApprovedEvent;
import org.ticketing.match.domain.event.payload.MatchCanceledEvent;

public interface MatchEventPublisher {

    void publishMatchApproved(MatchApprovedEvent event);

    void publishMatchCanceled(MatchCanceledEvent event);
}
