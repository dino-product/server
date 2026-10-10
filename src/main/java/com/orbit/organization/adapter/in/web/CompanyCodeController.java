package com.orbit.organization.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.CompanyCodeControllerDocs;
import com.orbit.organization.application.port.in.query.GetCompanyCodeUseCase;
import com.orbit.organization.application.port.in.query.dto.GetCompanyCodeQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/company-code")
class CompanyCodeController implements CompanyCodeControllerDocs {

    private final GetCompanyCodeUseCase getCompanyCodeUseCase;

    CompanyCodeController(GetCompanyCodeUseCase getCompanyCodeUseCase) {
        this.getCompanyCodeUseCase = getCompanyCodeUseCase;
    }

    @Override
    @GetMapping
    public CompanyCodeResponse get(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return CompanyCodeResponse.from(
                getCompanyCodeUseCase.get(new GetCompanyCodeQuery(requester.accountId(), organizationId)));
    }
}
