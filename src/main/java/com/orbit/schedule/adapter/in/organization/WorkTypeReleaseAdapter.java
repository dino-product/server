package com.orbit.schedule.adapter.in.organization;

import java.util.List;

import org.springframework.stereotype.Component;

import com.orbit.organization.WorkTypeDeletionImpact;
import com.orbit.organization.WorkTypeRelease;
import com.orbit.organization.WorkTypeReleasedWork;
import com.orbit.schedule.application.port.in.command.ReleaseDeletedWorkTypeUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseDeletedWorkTypeCommand;
import com.orbit.schedule.application.port.in.query.CountWorksByTypeUseCase;
import com.orbit.schedule.application.port.in.query.dto.CountWorksByTypeQuery;
import com.orbit.schedule.application.port.in.query.dto.WorkTypeUsageInfo;

/**
 * organization이 선언한 작업 유형 삭제 요구 인터페이스를 schedule의 건수 조회·유형 해제 유즈케이스로 잇는 어댑터. organization의 트랜잭션 안에서 불리며, 결과를
 * organization의 타입으로 옮기기만 한다.
 */
@Component
class WorkTypeReleaseAdapter implements WorkTypeRelease {

    private final CountWorksByTypeUseCase countWorksByTypeUseCase;
    private final ReleaseDeletedWorkTypeUseCase releaseDeletedWorkTypeUseCase;

    WorkTypeReleaseAdapter(
            CountWorksByTypeUseCase countWorksByTypeUseCase,
            ReleaseDeletedWorkTypeUseCase releaseDeletedWorkTypeUseCase) {
        this.countWorksByTypeUseCase = countWorksByTypeUseCase;
        this.releaseDeletedWorkTypeUseCase = releaseDeletedWorkTypeUseCase;
    }

    @Override
    public WorkTypeDeletionImpact countImpact(Long organizationId, Long workTypeId) {
        WorkTypeUsageInfo usage = countWorksByTypeUseCase.count(new CountWorksByTypeQuery(organizationId, workTypeId));
        return new WorkTypeDeletionImpact(usage.beforeStart(), usage.started());
    }

    @Override
    public List<WorkTypeReleasedWork> releaseForDeletion(Long organizationId, Long workTypeId) {
        return releaseDeletedWorkTypeUseCase
                .release(new ReleaseDeletedWorkTypeCommand(organizationId, workTypeId))
                .stream()
                .map(released -> new WorkTypeReleasedWork(released.workId(), released.technicianId()))
                .toList();
    }
}
