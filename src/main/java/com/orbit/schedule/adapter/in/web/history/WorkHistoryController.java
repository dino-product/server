package com.orbit.schedule.adapter.in.web.history;

import java.time.Instant;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.WorkHistoryControllerDocs;
import com.orbit.schedule.application.port.in.query.GetWorkHistoryUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWorkHistoryQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkHistoryStatus;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/work-history")
class WorkHistoryController implements WorkHistoryControllerDocs {

    private final GetWorkHistoryUseCase getWorkHistoryUseCase;

    WorkHistoryController(GetWorkHistoryUseCase getWorkHistoryUseCase) {
        this.getWorkHistoryUseCase = getWorkHistoryUseCase;
    }

    @Override
    @GetMapping
    public WorkHistoryResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Set<Long> workTypeIds,
            @RequestParam(required = false) Set<WorkHistoryStatus> statuses) {
        return WorkHistoryResponse.from(getWorkHistoryUseCase.get(new GetWorkHistoryQuery(
                requester.accountId(), organizationId, technicianId, from, to, workTypeIds, statuses)));
    }
}
