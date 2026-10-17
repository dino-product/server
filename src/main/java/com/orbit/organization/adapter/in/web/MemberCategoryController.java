package com.orbit.organization.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.MemberCategoryControllerDocs;
import com.orbit.organization.application.port.in.command.CreateMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.DeleteMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.UpdateMemberCategoryUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateMemberCategoryCommand;
import com.orbit.organization.application.port.in.command.dto.DeleteMemberCategoryCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateMemberCategoryCommand;
import com.orbit.organization.application.port.in.query.ListMemberCategoriesUseCase;
import com.orbit.organization.application.port.in.query.dto.ListMemberCategoriesQuery;
import com.orbit.organization.domain.MemberCategoryKind;

/** 직원 유형(staff-categories)과 기사 유형(technician-categories)은 같은 규칙을 쓰는 별개 자원이라 경로로 종류를 정한다. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class MemberCategoryController implements MemberCategoryControllerDocs {

    private final ListMemberCategoriesUseCase listMemberCategoriesUseCase;
    private final CreateMemberCategoryUseCase createMemberCategoryUseCase;
    private final UpdateMemberCategoryUseCase updateMemberCategoryUseCase;
    private final DeleteMemberCategoryUseCase deleteMemberCategoryUseCase;

    MemberCategoryController(
            ListMemberCategoriesUseCase listMemberCategoriesUseCase,
            CreateMemberCategoryUseCase createMemberCategoryUseCase,
            UpdateMemberCategoryUseCase updateMemberCategoryUseCase,
            DeleteMemberCategoryUseCase deleteMemberCategoryUseCase) {
        this.listMemberCategoriesUseCase = listMemberCategoriesUseCase;
        this.createMemberCategoryUseCase = createMemberCategoryUseCase;
        this.updateMemberCategoryUseCase = updateMemberCategoryUseCase;
        this.deleteMemberCategoryUseCase = deleteMemberCategoryUseCase;
    }

    @Override
    @GetMapping("/staff-categories")
    public MemberCategoriesResponse listStaffCategories(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return list(requester, organizationId, MemberCategoryKind.STAFF);
    }

    @Override
    @PostMapping("/staff-categories")
    public MemberCategoryResponse createStaffCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody MemberCategoryRequest request) {
        return create(requester, organizationId, MemberCategoryKind.STAFF, request);
    }

    @Override
    @PutMapping("/staff-categories/{categoryId}")
    public MemberCategoryResponse updateStaffCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long categoryId,
            @Valid @RequestBody MemberCategoryRequest request) {
        return update(requester, organizationId, MemberCategoryKind.STAFF, categoryId, request);
    }

    @Override
    @DeleteMapping("/staff-categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStaffCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long categoryId) {
        delete(requester, organizationId, MemberCategoryKind.STAFF, categoryId);
    }

    @Override
    @GetMapping("/technician-categories")
    public MemberCategoriesResponse listTechnicianCategories(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return list(requester, organizationId, MemberCategoryKind.TECHNICIAN);
    }

    @Override
    @PostMapping("/technician-categories")
    public MemberCategoryResponse createTechnicianCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody MemberCategoryRequest request) {
        return create(requester, organizationId, MemberCategoryKind.TECHNICIAN, request);
    }

    @Override
    @PutMapping("/technician-categories/{categoryId}")
    public MemberCategoryResponse updateTechnicianCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long categoryId,
            @Valid @RequestBody MemberCategoryRequest request) {
        return update(requester, organizationId, MemberCategoryKind.TECHNICIAN, categoryId, request);
    }

    @Override
    @DeleteMapping("/technician-categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTechnicianCategory(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long categoryId) {
        delete(requester, organizationId, MemberCategoryKind.TECHNICIAN, categoryId);
    }

    private MemberCategoriesResponse list(AccountPrincipal requester, Long organizationId, MemberCategoryKind kind) {
        return MemberCategoriesResponse.from(listMemberCategoriesUseCase.list(
                new ListMemberCategoriesQuery(requester.accountId(), organizationId, kind)));
    }

    private MemberCategoryResponse create(
            AccountPrincipal requester, Long organizationId, MemberCategoryKind kind, MemberCategoryRequest request) {
        return MemberCategoryResponse.from(createMemberCategoryUseCase.create(new CreateMemberCategoryCommand(
                requester.accountId(), organizationId, kind, request.name(), request.color())));
    }

    private MemberCategoryResponse update(
            AccountPrincipal requester,
            Long organizationId,
            MemberCategoryKind kind,
            Long categoryId,
            MemberCategoryRequest request) {
        return MemberCategoryResponse.from(updateMemberCategoryUseCase.update(new UpdateMemberCategoryCommand(
                requester.accountId(), organizationId, kind, categoryId, request.name(), request.color())));
    }

    private void delete(AccountPrincipal requester, Long organizationId, MemberCategoryKind kind, Long categoryId) {
        deleteMemberCategoryUseCase.delete(
                new DeleteMemberCategoryCommand(requester.accountId(), organizationId, kind, categoryId));
    }
}
