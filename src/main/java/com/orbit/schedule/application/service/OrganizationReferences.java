package com.orbit.schedule.application.service;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.TechnicianContractPort;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.PlannedSchedule;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.shared.error.BusinessException;

/**
 * 작업이 참조하는 조직 자원(담당기사의 기사 계약, 작업 유형)이 그 조직에서 쓸 수 있는지 확인하는 공통 절차. 입력 형식·상태·같은 값 확인을 통과해 도메인에 반영한
 * 뒤, 저장 전에 호출한다. 외부 확인이 드는 일이라 더 싼 확인에서 끝날 요청은 부르지 않는다. 다른 조직의 자원과 없는 자원을 구분하지 않는다.
 */
final class OrganizationReferences {

    private OrganizationReferences() {}

    /**
     * 배정·재배정한 일정의 담당기사가 작업 조직의 활성 기사 계약이 아니면 400(SCHEDULE-013). 그 기사를 잠근 뒤 부른다. 기사 비활성화·역할 변경의 대기함
     * 반환(HM-252)도 같은 기사를 잠그고 배정을 풀어야, 반환과 동시에 실행된 배정이 비활성 기사에게 남지 않는다.
     */
    static void requireActiveTechnician(TechnicianContractPort port, Work work, WorkSchedule schedule) {
        if (!port.isActive(work.organizationId(), schedule.technicianId())) {
            throw new BusinessException(ScheduleErrorCode.TECHNICIAN_NOT_ASSIGNABLE);
        }
    }

    /**
     * 대기함 작업이 미리 입력한 담당기사가 작업 조직의 활성 기사 계약이 아니면 400(SCHEDULE-013). 미리 입력한 담당기사가 없으면 확인하지 않는다. 기사 일정을 차지하지
     * 않으므로 잠그지 않는다.
     */
    static void requireActivePlannedTechnician(TechnicianContractPort port, Work work) {
        work.plannedSchedule().map(PlannedSchedule::technicianId).ifPresent(technicianId -> {
            if (!port.isActive(work.organizationId(), technicianId)) {
                throw new BusinessException(ScheduleErrorCode.TECHNICIAN_NOT_ASSIGNABLE);
            }
        });
    }

    /** 작업에 지정한 작업 유형이 작업 조직에서 선택할 수 없는 유형이면 400(SCHEDULE-014). 유형을 비웠으면 확인하지 않는다. */
    static void requireSelectableWorkType(WorkTypePort port, Work work) {
        work.workType().ifPresent(workType -> {
            if (!port.isSelectable(work.organizationId(), workType)) {
                throw new BusinessException(ScheduleErrorCode.WORK_TYPE_NOT_SELECTABLE);
            }
        });
    }
}
