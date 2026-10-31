package com.orbit.schedule.adapter.in.web.board;

import java.time.Instant;
import java.util.concurrent.Semaphore;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
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
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 실시간 신호마다 열린 화면이 보드 전체를 다시 조회하므로, 신호가 몰리면 커넥션과 CPU를 기다리는 조회가 쌓인다. 동시에 처리할 수 있는 만큼만 받고 나머지는
 * 곧바로 거절해, 화면이 잠시 뒤 다시 조회하게 한다. 보드는 다음 신호나 재조회로 맞춰지므로 거절해도 화면이 늦을 뿐 틀어지지 않는다.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/progress-board")
class ProgressBoardController implements ProgressBoardControllerDocs {

    private final GetProgressBoardUseCase getProgressBoardUseCase;
    private final ProgressBoardProperties properties;
    private final Semaphore permits;

    ProgressBoardController(GetProgressBoardUseCase getProgressBoardUseCase, ProgressBoardProperties properties) {
        this.getProgressBoardUseCase = getProgressBoardUseCase;
        this.properties = properties;
        this.permits = new Semaphore(properties.maxConcurrentRequests());
    }

    @Override
    @GetMapping
    public ProgressBoardResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletResponse response) {
        if (!permits.tryAcquire()) {
            // 전역 처리기는 Content-Type만 다시 정하므로 미리 넣은 헤더가 오류 응답에 남는다.
            response.setHeader(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(properties.retryAfter().toSeconds()));
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        try {
            return ProgressBoardResponse.from(getProgressBoardUseCase.get(
                    new GetProgressBoardQuery(requester.accountId(), organizationId, from, to)));
        } finally {
            permits.release();
        }
    }
}
