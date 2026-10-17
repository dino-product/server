package com.orbit.schedule.adapter.in.web.timetable;

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
import com.orbit.schedule.adapter.in.web.docs.TimetableControllerDocs;
import com.orbit.schedule.application.port.in.query.GetTimetableUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetTimetableQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/timetable")
class TimetableController implements TimetableControllerDocs {

    private final GetTimetableUseCase getTimetableUseCase;

    TimetableController(GetTimetableUseCase getTimetableUseCase) {
        this.getTimetableUseCase = getTimetableUseCase;
    }

    @Override
    @GetMapping
    public TimetableResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Set<Long> technicianTypeIds,
            @RequestParam(required = false) Set<Long> workTypeIds) {
        return TimetableResponse.from(getTimetableUseCase.get(new GetTimetableQuery(
                requester.accountId(), organizationId, from, to, technicianTypeIds, workTypeIds)));
    }
}
