package org.ticketing.match.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketing.match.application.dto.command.AddMatchZonePolicyCommand;
import org.ticketing.match.application.dto.command.ChangeMatchStatusCommand;
import org.ticketing.match.application.dto.command.CreateMatchCommand;
import org.ticketing.match.application.dto.command.DeleteMatchCommand;
import org.ticketing.match.application.dto.command.RemoveMatchZonePolicyCommand;
import org.ticketing.match.application.dto.command.UpdateMatchCommand;
import org.ticketing.match.application.dto.command.UpdateMatchZonePolicyCommand;
import org.ticketing.match.application.dto.query.FindMatchQuery;
import org.ticketing.match.application.dto.result.MatchResult;
import org.ticketing.match.application.dto.result.MatchZonePolicyResult;

@Service
@Transactional(readOnly = true)
public class MatchApplicationService {

    // ──────────────────────────────────────────
    // Match CRUD
    // ──────────────────────────────────────────

    @Transactional
    public MatchResult createMatch(CreateMatchCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public MatchResult findMatch(FindMatchQuery query) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public MatchResult updateMatch(UpdateMatchCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public MatchResult changeStatus(ChangeMatchStatusCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public void deleteMatch(DeleteMatchCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ──────────────────────────────────────────
    // ZonePolicy — Match 어그리게이트를 통한 접근
    // ──────────────────────────────────────────

    @Transactional
    public MatchZonePolicyResult addZonePolicy(AddMatchZonePolicyCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public MatchZonePolicyResult updateZonePolicy(UpdateMatchZonePolicyCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public void removeZonePolicy(RemoveMatchZonePolicyCommand command) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
