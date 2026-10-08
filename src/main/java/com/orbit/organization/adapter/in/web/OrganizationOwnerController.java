package com.orbit.organization.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.OrganizationOwnerControllerDocs;
import com.orbit.organization.application.port.in.command.DesignateOwnerUseCase;
import com.orbit.organization.application.port.in.command.RevokeOwnerUseCase;
import com.orbit.organization.application.port.in.command.dto.DesignateOwnerCommand;
import com.orbit.organization.application.port.in.command.dto.RevokeOwnerCommand;

@Validated
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/owners")
class OrganizationOwnerController implements OrganizationOwnerControllerDocs {

    private final DesignateOwnerUseCase designateOwnerUseCase;
    private final RevokeOwnerUseCase revokeOwnerUseCase;

    OrganizationOwnerController(DesignateOwnerUseCase designateOwnerUseCase, RevokeOwnerUseCase revokeOwnerUseCase) {
        this.designateOwnerUseCase = designateOwnerUseCase;
        this.revokeOwnerUseCase = revokeOwnerUseCase;
    }

    @Override
    @PutMapping("/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void designate(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long membershipId) {
        designateOwnerUseCase.designate(new DesignateOwnerCommand(requester.accountId(), organizationId, membershipId));
    }

    @Override
    @DeleteMapping("/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long membershipId) {
        revokeOwnerUseCase.revoke(new RevokeOwnerCommand(requester.accountId(), organizationId, membershipId));
    }
}
