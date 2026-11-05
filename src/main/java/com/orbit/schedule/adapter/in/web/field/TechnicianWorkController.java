package com.orbit.schedule.adapter.in.web.field;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.TechnicianWorkControllerDocs;
import com.orbit.schedule.application.port.in.command.AcceptWorkUseCase;
import com.orbit.schedule.application.port.in.command.RejectWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.AcceptWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.RejectWorkCommand;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works/{workId}")
class TechnicianWorkController implements TechnicianWorkControllerDocs {

    private final AcceptWorkUseCase acceptWorkUseCase;
    private final RejectWorkUseCase rejectWorkUseCase;

    TechnicianWorkController(AcceptWorkUseCase acceptWorkUseCase, RejectWorkUseCase rejectWorkUseCase) {
        this.acceptWorkUseCase = acceptWorkUseCase;
        this.rejectWorkUseCase = rejectWorkUseCase;
    }

    @Override
    @PostMapping("/acceptance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId,
            @Valid @RequestBody AssignmentNumberRequest request) {
        acceptWorkUseCase.accept(
                new AcceptWorkCommand(requester.accountId(), organizationId, workId, request.assignmentNumber()));
    }

    @Override
    @PostMapping("/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId,
            @Valid @RequestBody RejectWorkRequest request) {
        rejectWorkUseCase.reject(new RejectWorkCommand(
                requester.accountId(),
                organizationId,
                workId,
                request.assignmentNumber(),
                request.reason(),
                request.note()));
    }
}
