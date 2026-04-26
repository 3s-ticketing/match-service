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
import org.ticketing.match.application.dto.command.DeleteMatchCommand;
import org.ticketing.match.application.dto.command.RemoveMatchZonePolicyCommand;
import org.ticketing.match.application.dto.query.FindMatchQuery;
import org.ticketing.match.application.service.MatchApplicationService;
import org.ticketing.match.infrastructure.security.SecurityContextProvider;
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
    private final SecurityContextProvider securityContextProvider;

    // ──────────────────────────────────────────
    // Match
    // ──────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponseDto createMatch(@RequestBody @Valid CreateMatchRequestDto request) {
        return MatchResponseDto.from(
                matchApplicationService.createMatch(request.toCommand())
        );
    }

    @GetMapping("/{matchId}")
    public MatchResponseDto getMatch(@PathVariable UUID matchId) {
        return MatchResponseDto.from(
                matchApplicationService.findMatch(new FindMatchQuery(matchId))
        );
    }

    @PutMapping("/{matchId}")
    public MatchResponseDto updateMatch(
            @PathVariable UUID matchId,
            @RequestBody @Valid UpdateMatchRequestDto request
    ) {
        return MatchResponseDto.from(
                matchApplicationService.updateMatch(request.toCommand(matchId))
        );
    }

    @PatchMapping("/{matchId}/status")
    public MatchResponseDto changeStatus(
            @PathVariable UUID matchId,
            @RequestBody @Valid ChangeMatchStatusRequestDto request
    ) {
        return MatchResponseDto.from(
                matchApplicationService.changeStatus(request.toCommand(matchId))
        );
    }

    @DeleteMapping("/{matchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMatch(@PathVariable UUID matchId) {
        String deletedBy = securityContextProvider.getCurrentUsername();
        matchApplicationService.deleteMatch(new DeleteMatchCommand(matchId, deletedBy));
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
        return MatchZonePolicyResponseDto.from(
                matchApplicationService.addZonePolicy(request.toCommand(matchId))
        );
    }

    @PutMapping("/{matchId}/zone-policies/{policyId}")
    public MatchZonePolicyResponseDto updateZonePolicy(
            @PathVariable UUID matchId,
            @PathVariable UUID policyId,
            @RequestBody @Valid UpdateMatchZonePolicyRequestDto request
    ) {
        return MatchZonePolicyResponseDto.from(
                matchApplicationService.updateZonePolicy(request.toCommand(matchId, policyId))
        );
    }

    @DeleteMapping("/{matchId}/zone-policies/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeZonePolicy(
            @PathVariable UUID matchId,
            @PathVariable UUID policyId
    ) {
        String deletedBy = securityContextProvider.getCurrentUsername();
        matchApplicationService.removeZonePolicy(new RemoveMatchZonePolicyCommand(matchId, policyId, deletedBy));
    }
}
