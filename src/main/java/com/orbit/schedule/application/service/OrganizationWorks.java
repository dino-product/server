package com.orbit.schedule.application.service;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.shared.error.BusinessException;

/**
 * 요청한 조직의 작업을 찾는 공통 절차. 행위자 확인 뒤에 호출한다. 작업 식별자 형식이 틀리면 400, 없거나 다른 조직의 작업이면 존재 여부가 드러나지 않도록 404(SCHEDULE-001)로
 * 처리한다. 조직 범위는 저장소 조회가 보장한다. 작업을 읽기 전에 기사를 잠그는 유즈케이스는 형식 확인({@link #requireId})을 잠금 앞에, 조회를 잠금 뒤에 따로 부른다.
 */
final class OrganizationWorks {

    private OrganizationWorks() {}

    static Work require(WorkRepository workRepository, OrganizationId organizationId, Long workIdValue) {
        return require(workRepository, organizationId, requireId(workIdValue));
    }

    /** 형식 확인을 마친 식별자로 요청한 조직의 작업을 찾는다(없으면 404). */
    static Work require(WorkRepository workRepository, OrganizationId organizationId, WorkId workId) {
        return workRepository
                .findInOrganization(organizationId, workId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.WORK_NOT_FOUND));
    }

    /** 작업 식별자 형식만 확인한다(400). 저장소를 읽지 않는다. */
    static WorkId requireId(Long workIdValue) {
        return DomainRuleViolations.call(() -> new WorkId(workIdValue));
    }
}
