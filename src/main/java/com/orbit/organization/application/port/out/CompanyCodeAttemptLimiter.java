package com.orbit.organization.application.port.out;

import com.orbit.organization.domain.AccountId;

/**
 * 회사 코드 무차별 대입을 막으려고 계정별로 일정 시간 안의 회사 확인 실패를 센다. 회사 확인과 참여 요청이 같은 횟수를 나눠 쓴다. 한도와 시간 창은 구현이 정한다.
 *
 * <p>동시에 몰아 보낸 요청도 한도를 넘지 못하도록 코드를 찾기 전에 시도를 먼저 세고, 실패가 아니었으면 되돌린다.
 */
public interface CompanyCodeAttemptLimiter {

    /** 시도를 하나 세고, 센 결과가 한도 안이면 {@code true}. 한도를 넘었으면 코드를 찾아보지 않고 거부한다. */
    boolean tryAttempt(AccountId accountId);

    /** 현재 코드나 이전 코드로 판정된 시도를 되돌린다. 이전 코드는 실제 회사를 맞힌 것이라 실패로 세지 않는다. */
    void refund(AccountId accountId);
}
