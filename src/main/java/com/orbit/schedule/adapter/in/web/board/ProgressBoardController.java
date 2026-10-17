package com.orbit.schedule.adapter.in.web.board;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.ProgressBoardControllerDocs;
import com.orbit.schedule.application.port.in.query.GetProgressBoardUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetProgressBoardQuery;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/progress-board")
class ProgressBoardController implements ProgressBoardControllerDocs {

    private final GetProgressBoardUseCase getProgressBoardUseCase;

    ProgressBoardController(GetProgressBoardUseCase getProgressBoardUseCase) {
        this.getProgressBoardUseCase = getProgressBoardUseCase;
    }

    @Override
    @GetMapping
    public ProgressBoardResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ProgressBoardResponse.from(getProgressBoardUseCase.get(
                new GetProgressBoardQuery(requester.accountId(), organizationId, from, to)));
    }
}
