package com.orbit.schedule.adapter.in.web.detail;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.WorkDetailControllerDocs;
import com.orbit.schedule.application.port.in.query.GetWorkDetailUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWorkDetailQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/works/{workId}")
class WorkDetailController implements WorkDetailControllerDocs {

    private final GetWorkDetailUseCase getWorkDetailUseCase;

    WorkDetailController(GetWorkDetailUseCase getWorkDetailUseCase) {
        this.getWorkDetailUseCase = getWorkDetailUseCase;
    }

    @Override
    @GetMapping
    public WorkDetailResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId) {
        return WorkDetailResponse.from(
                getWorkDetailUseCase.get(new GetWorkDetailQuery(requester.accountId(), organizationId, workId)));
    }
}
