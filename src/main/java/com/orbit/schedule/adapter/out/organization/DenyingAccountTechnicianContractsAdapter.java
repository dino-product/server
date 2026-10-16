package com.orbit.schedule.adapter.out.organization;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;

/**
 * 임시 AccountTechnicianContractsPort 구현. organization이 계정의 기사 계약 목록을 조회하는 공개 계약을 아직 제공하지 않는다. 빈 목록을 돌려주면 작업중인
 * 기사의 탈퇴를 막지 않고 예정 작업도 돌리지 않는 가짜 성공이 되므로 모든 조회를 거부한다. 그 계약이 생기면 이를 호출하는 어댑터로 교체한 뒤 삭제한다(HM-321).
 * {@code local}·{@code test} 프로필에서만 등록하며 그 밖의 프로필은 Bean 부재로 기동하지 않는다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class DenyingAccountTechnicianContractsAdapter implements AccountTechnicianContractsPort {

    private static final String MESSAGE = "organization의 계정 기사 계약 조회 계약(HM-321) 전에는 지원하지 않는다";

    @Override
    public List<AccountTechnicianContract> listByAccount(Long accountId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
