package com.orbit.organization.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.ParticipationRequestControllerDocs;
import com.orbit.organization.application.port.in.command.CancelParticipationRequestUseCase;
import com.orbit.organization.application.port.in.command.RequestParticipationUseCase;
import com.orbit.organization.application.port.in.command.dto.CancelParticipationRequestCommand;
import com.orbit.organization.application.port.in.command.dto.RequestParticipationCommand;
import com.orbit.organization.application.port.in.query.ListMyParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListMyParticipationRequestsQuery;

@RestController
@RequestMapping("/api/v1/participation-requests")
class ParticipationRequestController implements ParticipationRequestControllerDocs {

    private final RequestParticipationUseCase requestParticipationUseCase;
    private final CancelParticipationRequestUseCase cancelParticipationRequestUseCase;
    private final ListMyParticipationRequestsUseCase listMyParticipationRequestsUseCase;

    ParticipationRequestController(
            RequestParticipationUseCase requestParticipationUseCase,
            CancelParticipationRequestUseCase cancelParticipationRequestUseCase,
            ListMyParticipationRequestsUseCase listMyParticipationRequestsUseCase) {
        this.requestParticipationUseCase = requestParticipationUseCase;
        this.cancelParticipationRequestUseCase = cancelParticipationRequestUseCase;
        this.listMyParticipationRequestsUseCase = listMyParticipationRequestsUseCase;
    }

    @Override
    @GetMapping
    public MyParticipationRequestsResponse listMine(@AuthenticationPrincipal AccountPrincipal requester) {
        return MyParticipationRequestsResponse.from(
                listMyParticipationRequestsUseCase.list(new ListMyParticipationRequestsQuery(requester.accountId())));
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

    @Override
    @PostMapping("/{requestId}/cancel")
    public ParticipationRequestResponse cancel(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long requestId) {
        return ParticipationRequestResponse.from(cancelParticipationRequestUseCase.cancel(
                new CancelParticipationRequestCommand(requester.accountId(), requestId)));
    }
}
