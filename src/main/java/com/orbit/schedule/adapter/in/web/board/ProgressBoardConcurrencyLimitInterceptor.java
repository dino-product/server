package com.orbit.schedule.adapter.in.web.board;

import java.util.concurrent.Semaphore;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 실시간 신호마다 열린 화면이 보드 전체를 다시 조회하므로, 신호가 몰리면 커넥션과 CPU를 기다리는 조회가 쌓인다. 인스턴스가 동시에 처리할 수 있는 만큼만 받고
 * 나머지는 기다리지 않고 거절해, 화면이 잠시 뒤 다시 조회하게 한다. 보드는 다음 신호나 재조회로 맞춰지므로 거절해도 화면이 늦을 뿐 틀어지지 않는다.
 *
 * <p>컨트롤러가 아니라 인터셉터에서 거르는 것은, 가입 공통 검사(profile)처럼 DB를 쓰는 앞선 인터셉터보다 먼저 거절해 거절할 조회가 커넥션을 기다리지 않게 하기
 * 위해서다. 순서는 {@link ProgressBoardWebConfig}가 정한다. 자리는 이 인터셉터가 통과시킨 요청이 끝날 때만 돌려준다.
 */
class ProgressBoardConcurrencyLimitInterceptor implements HandlerInterceptor {

    static final String PATH_PATTERN = "/api/v1/organizations/*/progress-board";

    private final Semaphore permits;
    private final String retryAfterSeconds;

    ProgressBoardConcurrencyLimitInterceptor(ProgressBoardProperties properties) {
        this.permits = new Semaphore(properties.maxConcurrentRequests());
        this.retryAfterSeconds = Long.toString(properties.retryAfter().toSeconds());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!permits.tryAcquire()) {
            // 전역 처리기는 Content-Type만 다시 정하므로 미리 넣은 헤더가 오류 응답에 남는다.
            response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
            throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        permits.release();
    }
}
