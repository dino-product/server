package com.orbit.schedule.adapter.in.web.myschedule;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.MyScheduleControllerDocs;
import com.orbit.schedule.application.port.in.query.GetActionableWorksUseCase;
import com.orbit.schedule.application.port.in.query.GetMyScheduleUseCase;
import com.orbit.schedule.application.port.in.query.GetPendingWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetMyScheduleQuery;
import com.orbit.schedule.application.port.in.query.dto.GetTechnicianWorksQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/my-schedule")
class MyScheduleController implements MyScheduleControllerDocs {

    private final GetMyScheduleUseCase getMyScheduleUseCase;
    private final GetPendingWorksUseCase getPendingWorksUseCase;
    private final GetActionableWorksUseCase getActionableWorksUseCase;

    MyScheduleController(
            GetMyScheduleUseCase getMyScheduleUseCase,
            GetPendingWorksUseCase getPendingWorksUseCase,
            GetActionableWorksUseCase getActionableWorksUseCase) {
        this.getMyScheduleUseCase = getMyScheduleUseCase;
        this.getPendingWorksUseCase = getPendingWorksUseCase;
        this.getActionableWorksUseCase = getActionableWorksUseCase;
    }

    @Override
    @GetMapping
    public MyScheduleResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return MyScheduleResponse.from(
                getMyScheduleUseCase.get(new GetMyScheduleQuery(requester.accountId(), organizationId, from, to)));
    }

    @Override
    @GetMapping("/pending")
    public MyScheduleResponse pending(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return MyScheduleResponse.from(
                getPendingWorksUseCase.get(new GetTechnicianWorksQuery(requester.accountId(), organizationId)));
    }

    @Override
    @GetMapping("/actionable")
    public MyScheduleResponse actionable(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long organizationId) {
        return MyScheduleResponse.from(
                getActionableWorksUseCase.get(new GetTechnicianWorksQuery(requester.accountId(), organizationId)));
    }
}
