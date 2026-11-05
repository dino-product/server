package com.orbit.schedule.adapter.in.web.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@DisplayName("진행 보드 컨트롤러")
class ProgressBoardControllerTest {

    private static final Instant FROM = Instant.parse("2030-03-02T00:00:00Z");

    @Test
    @DisplayName("지금 보드를 만들 수 없으면(503) Retry-After를 붙이고, 다른 오류에는 붙이지 않는다")
    void addsRetryAfterOnlyWhenUnavailable() {
        MockHttpServletResponse unavailable = new MockHttpServletResponse();
        ProgressBoardController busy = new ProgressBoardController(
                query -> {
                    throw new BusinessException(CommonErrorCode.SERVICE_UNAVAILABLE);
                },
                new ProgressBoardProperties(null, Duration.ofSeconds(3)));

        assertThatThrownBy(() -> busy.get(principal(), 1L, FROM, FROM.plusSeconds(60), unavailable))
                .isInstanceOf(BusinessException.class);
        assertThat(unavailable.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("3");

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        ProgressBoardController denied = new ProgressBoardController(
                query -> {
                    throw new BusinessException(ScheduleErrorCode.ACTION_NOT_ALLOWED);
                },
                new ProgressBoardProperties(null, null));

        assertThatThrownBy(() -> denied.get(principal(), 1L, FROM, FROM.plusSeconds(60), forbidden))
                .isInstanceOf(BusinessException.class);
        assertThat(forbidden.getHeader(HttpHeaders.RETRY_AFTER)).isNull();
    }

    private static AccountPrincipal principal() {
        return () -> 5L;
    }
}
