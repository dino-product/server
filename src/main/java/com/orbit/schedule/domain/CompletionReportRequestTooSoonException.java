package com.orbit.schedule.domain;

import java.time.Instant;

/**
 * 같은 작업의 마지막 완료보고 요청 뒤 {@link Work#COMPLETION_REPORT_REQUEST_INTERVAL}이 지나지 않았는데 다시 요청할 때. 상태·입력 오류와 구분해, 잠시 뒤
 * 다시 보내면 되는 요청임을 호출자가 알아볼 수 있게 한다.
 */
public final class CompletionReportRequestTooSoonException extends RuntimeException {

    public CompletionReportRequestTooSoonException(Instant nextRequestableAt) {
        super("completion report can be requested again at " + nextRequestableAt);
    }
}
