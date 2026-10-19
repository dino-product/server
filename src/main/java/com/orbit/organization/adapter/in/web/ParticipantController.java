package com.orbit.organization.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.ParticipantControllerDocs;
import com.orbit.organization.application.port.in.command.AssignMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.DeactivateParticipantUseCase;
import com.orbit.organization.application.port.in.command.ReactivateParticipantUseCase;
import com.orbit.organization.application.port.in.command.dto.AssignMemberCategoryCommand;
import com.orbit.organization.application.port.in.command.dto.ChangeParticipantStatusCommand;
import com.orbit.organization.application.port.in.query.ListParticipantsUseCase;
import com.orbit.organization.application.port.in.query.dto.ListParticipantsQuery;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.ParticipationType;

/** 참여 인원. 직원 소속(staff-memberships)과 기사 계약(technicians)은 식별자를 따로 매기므로 변경 경로로 종류를 정한다. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class ParticipantController implements ParticipantControllerDocs {

    private final ListParticipantsUseCase listParticipantsUseCase;
    private final AssignMemberCategoryUseCase assignMemberCategoryUseCase;
    private final DeactivateParticipantUseCase deactivateParticipantUseCase;
    private final ReactivateParticipantUseCase reactivateParticipantUseCase;

    ParticipantController(
            ListParticipantsUseCase listParticipantsUseCase,
            AssignMemberCategoryUseCase assignMemberCategoryUseCase,
            DeactivateParticipantUseCase deactivateParticipantUseCase,
            ReactivateParticipantUseCase reactivateParticipantUseCase) {
        this.listParticipantsUseCase = listParticipantsUseCase;
        this.assignMemberCategoryUseCase = assignMemberCategoryUseCase;
        this.deactivateParticipantUseCase = deactivateParticipantUseCase;
        this.reactivateParticipantUseCase = reactivateParticipantUseCase;
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

    @Override
    @PutMapping("/staff-memberships/{membershipId}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignStaffCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long membershipId,
            @RequestBody MemberCategoryAssignmentRequest request) {
        assign(requester, organizationId, MemberCategoryKind.STAFF, membershipId, request);
    }

    @Override
    @PutMapping("/technicians/{technicianId}/category")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignTechnicianCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long technicianId,
            @RequestBody MemberCategoryAssignmentRequest request) {
        assign(requester, organizationId, MemberCategoryKind.TECHNICIAN, technicianId, request);
    }

    @Override
    @PostMapping("/staff-memberships/{membershipId}/deactivation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateStaff(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long membershipId) {
        deactivateParticipantUseCase.deactivate(
                command(requester, organizationId, ParticipationType.STAFF, membershipId));
    }

    @Override
    @PostMapping("/staff-memberships/{membershipId}/reactivation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivateStaff(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long membershipId) {
        reactivateParticipantUseCase.reactivate(
                command(requester, organizationId, ParticipationType.STAFF, membershipId));
    }

    @Override
    @PostMapping("/technicians/{technicianId}/deactivation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateTechnician(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long technicianId) {
        deactivateParticipantUseCase.deactivate(
                command(requester, organizationId, ParticipationType.TECHNICIAN, technicianId));
    }

    @Override
    @PostMapping("/technicians/{technicianId}/reactivation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reactivateTechnician(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long technicianId) {
        reactivateParticipantUseCase.reactivate(
                command(requester, organizationId, ParticipationType.TECHNICIAN, technicianId));
    }

    private static ChangeParticipantStatusCommand command(
            AccountPrincipal requester, Long organizationId, ParticipationType type, Long memberId) {
        return new ChangeParticipantStatusCommand(requester.accountId(), organizationId, type, memberId);
    }

    private void assign(
            AccountPrincipal requester,
            Long organizationId,
            MemberCategoryKind kind,
            Long memberId,
            MemberCategoryAssignmentRequest request) {
        assignMemberCategoryUseCase.assign(new AssignMemberCategoryCommand(
                requester.accountId(), organizationId, kind, memberId, request.categoryId()));
    }
}
