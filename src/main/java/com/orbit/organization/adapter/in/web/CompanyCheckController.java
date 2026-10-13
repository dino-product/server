package com.orbit.organization.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.CompanyCheckControllerDocs;
import com.orbit.organization.application.port.in.query.CheckCompanyUseCase;
import com.orbit.organization.application.port.in.query.dto.CheckCompanyQuery;

@RestController
class CompanyCheckController implements CompanyCheckControllerDocs {

    private final CheckCompanyUseCase checkCompanyUseCase;

    CompanyCheckController(CheckCompanyUseCase checkCompanyUseCase) {
        this.checkCompanyUseCase = checkCompanyUseCase;
    }

    @Override
    @GetMapping("/api/v1/organizations/lookup")
    public CompanyCheckResponse check(
            @AuthenticationPrincipal AccountPrincipal requester, @RequestParam String companyCode) {
        return CompanyCheckResponse.from(
                checkCompanyUseCase.check(new CheckCompanyQuery(requester.accountId(), companyCode)));
    }
}
