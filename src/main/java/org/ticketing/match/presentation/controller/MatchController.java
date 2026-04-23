package org.ticketing.match.presentation.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.ticketing.match.application.service.MatchApplicationService;
import org.ticketing.match.presentation.dto.request.ChangeMatchStatusRequestDto;
import org.ticketing.match.presentation.dto.request.CreateMatchRequestDto;
import org.ticketing.match.presentation.dto.request.CreateMatchZonePolicyRequestDto;
import org.ticketing.match.presentation.dto.request.UpdateMatchRequestDto;
import org.ticketing.match.presentation.dto.request.UpdateMatchZonePolicyRequestDto;
import org.ticketing.match.presentation.dto.response.MatchResponseDto;
import org.ticketing.match.presentation.dto.response.MatchZonePolicyResponseDto;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchApplicationService matchApplicationService;

    // ──────────────────────────────────────────
    // Match
    // ──────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponseDto createMatch(@RequestBody @Valid CreateMatchRequestDto request) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @GetMapping("/{matchId}")
    public MatchResponseDto getMatch(@PathVariable UUID matchId) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{matchId}")
    public MatchResponseDto updateMatch(
            @PathVariable UUID matchId,
            @RequestBody @Valid UpdateMatchRequestDto request
    ) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PatchMapping("/{matchId}/status")
    public MatchResponseDto changeStatus(
            @PathVariable UUID matchId,
            @RequestBody @Valid ChangeMatchStatusRequestDto request
    ) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMatch(@PathVariable UUID matchId) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    // ──────────────────────────────────────────
    // ZonePolicy — Match 어그리게이트를 통한 접근
    // ──────────────────────────────────────────

    @PostMapping("/{matchId}/zone-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchZonePolicyResponseDto addZonePolicy(
            @PathVariable UUID matchId,
            @RequestBody @Valid CreateMatchZonePolicyRequestDto request
    ) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @PutMapping("/{matchId}/zone-policies/{policyId}")
    public MatchZonePolicyResponseDto updateZonePolicy(
            @PathVariable UUID matchId,
            @PathVariable UUID policyId,
            @RequestBody @Valid UpdateMatchZonePolicyRequestDto request
    ) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @DeleteMapping("/{matchId}/zone-policies/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeZonePolicy(
            @PathVariable UUID matchId,
            @PathVariable UUID policyId
    ) {
        // TODO: implement in CRUD branch
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
