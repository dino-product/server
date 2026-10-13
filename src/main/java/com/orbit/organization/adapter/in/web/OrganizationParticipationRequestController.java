package com.orbit.organization.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.OrganizationParticipationRequestControllerDocs;
import com.orbit.organization.application.port.in.query.ListPendingParticipationRequestsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListPendingParticipationRequestsQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/participation-requests")
class OrganizationParticipationRequestController implements OrganizationParticipationRequestControllerDocs {

    private final ListPendingParticipationRequestsUseCase listPendingUseCase;

    OrganizationParticipationRequestController(ListPendingParticipationRequestsUseCase listPendingUseCase) {
        this.listPendingUseCase = listPendingUseCase;
    }

    @Override
    @GetMapping
    public PendingParticipationRequestsResponse listPending(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return PendingParticipationRequestsResponse.from(listPendingUseCase.list(
                new ListPendingParticipationRequestsQuery(requester.accountId(), organizationId)));
    }
}
