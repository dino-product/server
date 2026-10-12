package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.AccountId;

/**
 * 회사 코드 무차별 대입을 막으려고 계정별로 일정 시간 안의 회사 확인 실패를 센다. 회사 확인과 참여 요청이 같은 횟수를 나눠 쓴다. 한도와 시간 창은 구현이 정한다.
 */
public interface CompanyCodeAttemptLimiter {

    /** 실패가 한도에 이르러 지금은 회사 코드를 확인할 수 없는지. */
    boolean isBlocked(AccountId accountId);

    /** 없는 코드·형식이 틀린 입력을 실패로 센다. 이전 코드는 실제 회사를 맞힌 것이라 세지 않는다. */
    void recordFailure(AccountId accountId);
}
