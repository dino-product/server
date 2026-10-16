package com.orbit.schedule.adapter.in.web.search;

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
import com.orbit.schedule.adapter.in.web.docs.WorkSearchControllerDocs;
import com.orbit.schedule.application.port.in.query.GetBacklogSummaryUseCase;
import com.orbit.schedule.application.port.in.query.SearchWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetBacklogSummaryQuery;
import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.domain.WorkStatus;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works")
class WorkSearchController implements WorkSearchControllerDocs {

    private final SearchWorksUseCase searchWorksUseCase;
    private final GetBacklogSummaryUseCase getBacklogSummaryUseCase;

    WorkSearchController(SearchWorksUseCase searchWorksUseCase, GetBacklogSummaryUseCase getBacklogSummaryUseCase) {
        this.searchWorksUseCase = searchWorksUseCase;
        this.getBacklogSummaryUseCase = getBacklogSummaryUseCase;
    }

    @Override
    @GetMapping
    public WorkSearchResponse search(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Set<WorkStatus> statuses,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long workTypeId,
            @RequestParam(required = false) Long registrarId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTo,
            @RequestParam(defaultValue = "false") boolean returnedByRejectionOnly,
            @RequestParam(defaultValue = "false") boolean delayedOnly,
            @RequestParam(required = false) SearchWorksQuery.Sort sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return WorkSearchResponse.from(searchWorksUseCase.search(new SearchWorksQuery(
                requester.accountId(),
                organizationId,
                keyword,
                statuses,
                technicianId,
                workTypeId,
                registrarId,
                startFrom,
                startTo,
                returnedByRejectionOnly,
                delayedOnly,
                sort,
                page,
                size)));
    }

    @Override
    @GetMapping("/backlog")
    public BacklogSummaryResponse backlog(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return BacklogSummaryResponse.from(getBacklogSummaryUseCase.get(
                new GetBacklogSummaryQuery(requester.accountId(), organizationId, page, size)));
    }
}
