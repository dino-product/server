package com.orbit.organization.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.docs.WorkTypeControllerDocs;
import com.orbit.organization.application.port.in.command.CreateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.UpdateWorkTypeUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateWorkTypeCommand;
import com.orbit.organization.application.port.in.command.dto.UpdateWorkTypeCommand;
import com.orbit.organization.application.port.in.query.ListWorkTypesUseCase;
import com.orbit.organization.application.port.in.query.dto.ListWorkTypesQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/work-types")
class WorkTypeController implements WorkTypeControllerDocs {

    private final ListWorkTypesUseCase listWorkTypesUseCase;
    private final CreateWorkTypeUseCase createWorkTypeUseCase;
    private final UpdateWorkTypeUseCase updateWorkTypeUseCase;

    WorkTypeController(
            ListWorkTypesUseCase listWorkTypesUseCase,
            CreateWorkTypeUseCase createWorkTypeUseCase,
            UpdateWorkTypeUseCase updateWorkTypeUseCase) {
        this.listWorkTypesUseCase = listWorkTypesUseCase;
        this.createWorkTypeUseCase = createWorkTypeUseCase;
        this.updateWorkTypeUseCase = updateWorkTypeUseCase;
    }

    @Override
    @GetMapping
    public WorkTypesResponse listWorkTypes(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return WorkTypesResponse.from(
                listWorkTypesUseCase.list(new ListWorkTypesQuery(requester.accountId(), organizationId)));
    }

    @Override
    @PostMapping
    public WorkTypeResponse createWorkType(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody WorkTypeRequest request) {
        return WorkTypeResponse.from(createWorkTypeUseCase.create(
                new CreateWorkTypeCommand(requester.accountId(), organizationId, request.name(), request.color())));
    }

    @Override
    @PutMapping("/{workTypeId}")
    public WorkTypeResponse updateWorkType(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workTypeId,
            @Valid @RequestBody WorkTypeRequest request) {
        return WorkTypeResponse.from(updateWorkTypeUseCase.update(new UpdateWorkTypeCommand(
                requester.accountId(), organizationId, workTypeId, request.name(), request.color())));
    }
}
