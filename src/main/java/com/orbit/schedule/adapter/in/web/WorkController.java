package com.orbit.schedule.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.WorkControllerDocs;
import com.orbit.schedule.application.port.in.command.CancelWorkUseCase;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works")
class WorkController implements WorkControllerDocs {

    private final UpdateWorkDetailsUseCase updateWorkDetailsUseCase;
    private final CancelWorkUseCase cancelWorkUseCase;

    WorkController(UpdateWorkDetailsUseCase updateWorkDetailsUseCase, CancelWorkUseCase cancelWorkUseCase) {
        this.updateWorkDetailsUseCase = updateWorkDetailsUseCase;
        this.cancelWorkUseCase = cancelWorkUseCase;
    }

    @Override
    @PutMapping("/{workId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDetails(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId,
            @Valid @RequestBody UpdateWorkDetailsRequest request) {
        WorkCustomerRequest customer = request.customer();
        WorkPaymentRequest payment = request.payment();
        updateWorkDetailsUseCase.update(new UpdateWorkDetailsCommand(
                requester.accountId(),
                organizationId,
                workId,
                request.name(),
                request.workTypeId(),
                customer == null ? null : customer.name(),
                customer == null ? null : customer.phone(),
                customer == null ? null : customer.address(),
                payment == null ? null : payment.fee(),
                payment == null ? null : payment.method()));
    }

    @Override
    @PostMapping("/{workId}/cancellation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId,
            @Valid @RequestBody CancelWorkRequest request) {
        cancelWorkUseCase.cancel(
                new CancelWorkCommand(requester.accountId(), organizationId, workId, request.reason(), request.note()));
    }
}
