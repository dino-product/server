package com.orbit.schedule.adapter.in.organization;

import org.springframework.stereotype.Component;

import com.orbit.organization.StaffRegisteredWorks;
import com.orbit.schedule.application.port.in.query.ExistsUnfinishedRegisteredWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.UnfinishedRegisteredWorksQuery;

/**
 * organization이 선언한 직원 등록 작업 확인 요구 인터페이스를 schedule의 조회 유즈케이스로 잇는 어댑터. organization이 발주사 행을 잠근 트랜잭션 안에서
 * 불리며, 인자를 조회로 옮기기만 한다.
 */
@Component
class StaffRegisteredWorksAdapter implements StaffRegisteredWorks {

    private final ExistsUnfinishedRegisteredWorksUseCase existsUnfinishedRegisteredWorksUseCase;

    StaffRegisteredWorksAdapter(ExistsUnfinishedRegisteredWorksUseCase existsUnfinishedRegisteredWorksUseCase) {
        this.existsUnfinishedRegisteredWorksUseCase = existsUnfinishedRegisteredWorksUseCase;
    }

    @Override
    public boolean existsUnfinished(Long organizationId, Long membershipId) {
        return existsUnfinishedRegisteredWorksUseCase.exists(
                new UnfinishedRegisteredWorksQuery(organizationId, membershipId));
    }
}
