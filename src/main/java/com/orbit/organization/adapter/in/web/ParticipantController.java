package com.orbit.organization.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.ParticipantControllerDocs;
import com.orbit.organization.application.port.in.query.ListParticipantsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListParticipantsQuery;
import com.orbit.organization.domain.ParticipationType;

/** 참여 인원. 직원 소속(staff-memberships)과 기사 계약(technicians)은 식별자를 따로 매기므로 변경 경로로 종류를 정한다. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class ParticipantController implements ParticipantControllerDocs {

    private final ListParticipantsUseCase listParticipantsUseCase;

    ParticipantController(ListParticipantsUseCase listParticipantsUseCase) {
        this.listParticipantsUseCase = listParticipantsUseCase;
    }

    @Override
    @GetMapping("/participants")
    public ParticipantsResponse listParticipants(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) ParticipationType type,
            @RequestParam(required = false) String name) {
        return ParticipantsResponse.from(listParticipantsUseCase.list(
                new ListParticipantsQuery(requester.accountId(), organizationId, type, name)));
    }
}
