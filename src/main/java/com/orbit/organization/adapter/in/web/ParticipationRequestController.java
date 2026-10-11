package com.orbit.organization.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.ParticipationRequestControllerDocs;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;

@RestController
@RequestMapping("/api/v1/participation-requests")
class ParticipationRequestController implements ParticipationRequestControllerDocs {

    private final RequestParticipationUseCase requestParticipationUseCase;

    ParticipationRequestController(RequestParticipationUseCase requestParticipationUseCase) {
        this.requestParticipationUseCase = requestParticipationUseCase;
    }

    @Override
    @PostMapping
    public ParticipationRequestResponse request(
            @AuthenticationPrincipal AccountPrincipal requester,
            @Valid @RequestBody RequestParticipationRequest request) {
        RequestParticipationCommand command =
                new RequestParticipationCommand(requester.accountId(), request.companyCode(), request.desiredType());
        return ParticipationRequestResponse.from(requestParticipationUseCase.request(command));
    }
}
