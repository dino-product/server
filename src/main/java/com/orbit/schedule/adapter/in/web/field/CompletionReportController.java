package com.orbit.schedule.adapter.in.web.field;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.CompletionReportControllerDocs;
import com.orbit.schedule.application.port.in.command.SubmitCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.in.query.GetCompletionReportUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetCompletionReportQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works/{workId}/completion-report")
class CompletionReportController implements CompletionReportControllerDocs {

    private final SubmitCompletionReportUseCase submitCompletionReportUseCase;
    private final GetCompletionReportUseCase getCompletionReportUseCase;

    CompletionReportController(
            SubmitCompletionReportUseCase submitCompletionReportUseCase,
            GetCompletionReportUseCase getCompletionReportUseCase) {
        this.submitCompletionReportUseCase = submitCompletionReportUseCase;
        this.getCompletionReportUseCase = getCompletionReportUseCase;
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId,
            @Valid @RequestBody SubmitCompletionReportRequest request) {
        submitCompletionReportUseCase.submit(new SubmitCompletionReportCommand(
                requester.accountId(),
                organizationId,
                workId,
                request.assignmentNumber(),
                request.beforePhotoIds(),
                request.afterPhotoIds(),
                request.usedParts(),
                request.workNote(),
                request.actualFee(),
                request.actualPaymentMethod()));
    }

    @Override
    @GetMapping
    public WorkCompletionReportResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId) {
        return WorkCompletionReportResponse.from(getCompletionReportUseCase.get(
                new GetCompletionReportQuery(requester.accountId(), organizationId, workId)));
    }
}
