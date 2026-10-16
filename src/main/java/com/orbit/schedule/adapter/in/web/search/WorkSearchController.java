package com.orbit.schedule.adapter.in.web.search;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.WorkSearchControllerDocs;
import com.orbit.schedule.application.port.in.query.ExportWorksUseCase;
import com.orbit.schedule.application.port.in.query.GetBacklogSummaryUseCase;
import com.orbit.schedule.application.port.in.query.SearchWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.ExportWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.GetBacklogSummaryQuery;
import com.orbit.schedule.application.port.in.query.dto.SearchWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkExportInfo;
import com.orbit.schedule.application.port.in.query.dto.WorkSearchFilter;
import com.orbit.schedule.domain.WorkStatus;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works")
class WorkSearchController implements WorkSearchControllerDocs {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SearchWorksUseCase searchWorksUseCase;
    private final GetBacklogSummaryUseCase getBacklogSummaryUseCase;
    private final ExportWorksUseCase exportWorksUseCase;

    WorkSearchController(
            SearchWorksUseCase searchWorksUseCase,
            GetBacklogSummaryUseCase getBacklogSummaryUseCase,
            ExportWorksUseCase exportWorksUseCase) {
        this.searchWorksUseCase = searchWorksUseCase;
        this.getBacklogSummaryUseCase = getBacklogSummaryUseCase;
        this.exportWorksUseCase = exportWorksUseCase;
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
            @RequestParam(required = false) WorkSearchFilter.Sort sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        WorkSearchFilter filter = new WorkSearchFilter(
                keyword,
                statuses,
                technicianId,
                workTypeId,
                registrarId,
                startFrom,
                startTo,
                returnedByRejectionOnly,
                delayedOnly,
                sort);
        return WorkSearchResponse.from(searchWorksUseCase.search(
                new SearchWorksQuery(requester.accountId(), organizationId, filter, page, size)));
    }

    @Override
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
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
            @RequestParam(required = false) WorkSearchFilter.Sort sort,
            @RequestParam(defaultValue = "Asia/Seoul") ZoneId timeZone) {
        WorkSearchFilter filter = new WorkSearchFilter(
                keyword,
                statuses,
                technicianId,
                workTypeId,
                registrarId,
                startFrom,
                startTo,
                returnedByRejectionOnly,
                delayedOnly,
                sort);
        WorkExportInfo exported =
                exportWorksUseCase.export(new ExportWorksQuery(requester.accountId(), organizationId, filter));
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(WorkExportWorkbook.fileName(exported, timeZone), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(WorkExportWorkbook.write(exported, timeZone));
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
