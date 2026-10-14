package com.orbit.schedule.adapter.in.web;

import java.time.Duration;

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
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.CancelWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works")
class WorkController implements WorkControllerDocs {

    private final CreateWorkUseCase createWorkUseCase;
    private final UpdateWorkDetailsUseCase updateWorkDetailsUseCase;
    private final CancelWorkUseCase cancelWorkUseCase;

    WorkController(
            CreateWorkUseCase createWorkUseCase,
            UpdateWorkDetailsUseCase updateWorkDetailsUseCase,
            CancelWorkUseCase cancelWorkUseCase) {
        this.createWorkUseCase = createWorkUseCase;
        this.updateWorkDetailsUseCase = updateWorkDetailsUseCase;
        this.cancelWorkUseCase = cancelWorkUseCase;
    }

    @Override
    @PostMapping
    public CreatedWorkResponse create(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody CreateWorkRequest request) {
        WorkCustomerRequest customer = request.customer();
        WorkPaymentRequest payment = request.payment();
        WorkScheduleRequest schedule = request.schedule();
        Integer minutes = schedule == null ? null : schedule.expectedDurationMinutes();
        return CreatedWorkResponse.from(createWorkUseCase.create(new CreateWorkCommand(
                requester.accountId(),
                organizationId,
                request.name(),
                request.workTypeId(),
                customer == null ? null : customer.name(),
                customer == null ? null : customer.phone(),
                customer == null ? null : customer.address(),
                payment == null ? null : payment.fee(),
                payment == null ? null : payment.method(),
                schedule == null ? null : schedule.technicianId(),
                schedule == null ? null : schedule.startTime(),
                minutes == null ? null : Duration.ofMinutes(minutes),
                schedule != null && Boolean.TRUE.equals(schedule.conflictConfirmed()))));
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
