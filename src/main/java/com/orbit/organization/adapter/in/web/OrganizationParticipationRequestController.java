package com.orbit.organization.adapter.in.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.OrganizationParticipationRequestControllerDocs;
import com.orbit.organization.application.port.in.command.ApproveParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.RejectParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand;
import com.orbit.organization.application.port.in.command.dto.ApproveParticipationRequestsCommand.Approval;
import com.orbit.organization.application.port.in.command.dto.RejectParticipationRequestsCommand;
import com.orbit.organization.application.port.in.query.ListPendingParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListPendingParticipationRequestsQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/participation-requests")
class OrganizationParticipationRequestController implements OrganizationParticipationRequestControllerDocs {

    private final ListPendingParticipationRequestsUseCase listPendingUseCase;
    private final ApproveParticipationRequestsUseCase approveUseCase;
    private final RejectParticipationRequestsUseCase rejectUseCase;

    OrganizationParticipationRequestController(
            ListPendingParticipationRequestsUseCase listPendingUseCase,
            ApproveParticipationRequestsUseCase approveUseCase,
            RejectParticipationRequestsUseCase rejectUseCase) {
        this.listPendingUseCase = listPendingUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @Override
    @GetMapping
    public PendingParticipationRequestsResponse listPending(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return PendingParticipationRequestsResponse.from(listPendingUseCase.list(
                new ListPendingParticipationRequestsQuery(requester.accountId(), organizationId)));
    }

    @Override
    @PostMapping("/approvals")
    public ParticipationRequestDecisionsResponse approve(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody ApproveParticipationRequestsRequest request) {
        List<Approval> approvals = request.approvals().stream()
                .map(approval -> new Approval(approval.requestId(), approval.confirmedType()))
                .toList();
        return ParticipationRequestDecisionsResponse.from(approveUseCase.approve(
                new ApproveParticipationRequestsCommand(requester.accountId(), organizationId, approvals)));
    }

    @Override
    @PostMapping("/rejections")
    public ParticipationRequestDecisionsResponse reject(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody RejectParticipationRequestsRequest request) {
        return ParticipationRequestDecisionsResponse.from(rejectUseCase.reject(new RejectParticipationRequestsCommand(
                requester.accountId(), organizationId, request.requestIds(), request.reason())));
    }
}
