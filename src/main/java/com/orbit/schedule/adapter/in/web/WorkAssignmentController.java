package com.orbit.schedule.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.WorkAssignmentControllerDocs;
import com.orbit.schedule.application.port.in.command.UnassignWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.UnassignWorkCommand;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works/{workId}/assignment")
class WorkAssignmentController implements WorkAssignmentControllerDocs {

    private final UnassignWorkUseCase unassignWorkUseCase;

    WorkAssignmentController(UnassignWorkUseCase unassignWorkUseCase) {
        this.unassignWorkUseCase = unassignWorkUseCase;
    }

    @Override
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId) {
        unassignWorkUseCase.unassign(new UnassignWorkCommand(requester.accountId(), organizationId, workId));
    }
}
