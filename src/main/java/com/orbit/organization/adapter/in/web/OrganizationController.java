package com.orbit.organization.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.organization.adapter.in.web.docs.OrganizationControllerDocs;
import com.orbit.organization.application.port.in.command.CreateOrganizationUseCase;
import com.orbit.organization.application.port.in.command.dto.CreateOrganizationCommand;

@RestController
@RequestMapping("/api/v1/organizations")
class OrganizationController implements OrganizationControllerDocs {

    private final CreateOrganizationUseCase createOrganizationUseCase;
    private final RequesterAccountResolver requesterAccountResolver;

    OrganizationController(
            CreateOrganizationUseCase createOrganizationUseCase, RequesterAccountResolver requesterAccountResolver) {
        this.createOrganizationUseCase = createOrganizationUseCase;
        this.requesterAccountResolver = requesterAccountResolver;
    }

    @Override
    @PostMapping
    public CreatedOrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        CreateOrganizationCommand command =
                new CreateOrganizationCommand(requesterAccountResolver.resolve(), request.name(), request.industry());
        return CreatedOrganizationResponse.from(createOrganizationUseCase.create(command));
    }
}
