package com.orbit.schedule.adapter.in.web.board;

import java.time.Instant;

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
 * 진행 보드 조회. 유즈케이스가 지금 보드를 만들 수 없다고 하면(503 COMMON-503) 화면이 잠시 뒤 다시 조회하도록 {@code Retry-After}를 붙인다. 전역
 * 처리기는 Content-Type만 다시 정하므로 미리 넣은 헤더가 오류 응답에 남는다. 요청 수 상한은 {@link ProgressBoardConcurrencyLimitInterceptor}가 먼저 건다.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/progress-board")
class ProgressBoardController implements ProgressBoardControllerDocs {

    private final GetProgressBoardUseCase getProgressBoardUseCase;
    private final String retryAfterSeconds;

    ProgressBoardController(GetProgressBoardUseCase getProgressBoardUseCase, ProgressBoardProperties properties) {
        this.getProgressBoardUseCase = getProgressBoardUseCase;
        this.retryAfterSeconds = Long.toString(properties.retryAfter().toSeconds());
    }

    @Override
    @GetMapping
    public ProgressBoardResponse get(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletResponse response) {
        try {
            return ProgressBoardResponse.from(getProgressBoardUseCase.get(
                    new GetProgressBoardQuery(requester.accountId(), organizationId, from, to)));
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == CommonErrorCode.SERVICE_UNAVAILABLE) {
                response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
            }
            throw exception;
        }
    }
}
