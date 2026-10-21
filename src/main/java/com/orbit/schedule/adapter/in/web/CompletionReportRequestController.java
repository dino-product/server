package com.orbit.schedule.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.CompletionReportRequestControllerDocs;
import com.orbit.schedule.application.port.in.command.RequestCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.dto.RequestCompletionReportCommand;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works/{workId}/completion-report-requests")
class CompletionReportRequestController implements CompletionReportRequestControllerDocs {

    private final RequestCompletionReportUseCase requestCompletionReportUseCase;

    CompletionReportRequestController(RequestCompletionReportUseCase requestCompletionReportUseCase) {
        this.requestCompletionReportUseCase = requestCompletionReportUseCase;
    }

    @Override
    @PostMapping
    public CompletionReportRequestResponse request(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId) {
        return CompletionReportRequestResponse.from(requestCompletionReportUseCase.request(
                new RequestCompletionReportCommand(requester.accountId(), organizationId, workId)));
    }
}
