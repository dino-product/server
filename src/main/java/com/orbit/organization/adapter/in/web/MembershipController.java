package com.orbit.organization.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.MembershipControllerDocs;
import com.orbit.organization.application.port.in.query.ListMyMembershipsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListMyMembershipsQuery;

@RestController
@RequestMapping("/api/v1/memberships")
class MembershipController implements MembershipControllerDocs {

    private final ListMyMembershipsUseCase listMyMembershipsUseCase;

    MembershipController(ListMyMembershipsUseCase listMyMembershipsUseCase) {
        this.listMyMembershipsUseCase = listMyMembershipsUseCase;
    }

    @Override
    @GetMapping
    public MyMembershipsResponse listMine(@AuthenticationPrincipal AccountPrincipal requester) {
        return MyMembershipsResponse.from(
                listMyMembershipsUseCase.list(new ListMyMembershipsQuery(requester.accountId())));
    }
}
