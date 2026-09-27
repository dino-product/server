package com.orbit.schedule.application.service;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.TechnicianScheduleBusyException;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/**
 * 기사의 활성 작업을 읽고 판정하는 유즈케이스가 같은 기사를 동시에 바꾸지 못하게, 읽기 전에 잠그는 공통 절차. 어느 유즈케이스가 잠가야 하는지는 schedule 지침이
 * 원본이다. 대기 한도를 넘기면 409(SCHEDULE-006)로 바꾸고, 트랜잭션은 예외로 롤백된다.
 */
final class TechnicianScheduleLocks {

    private TechnicianScheduleLocks() {}

    static void lock(LockTechnicianSchedulePort port, OrganizationId organizationId, MembershipId technicianId) {
        try {
            port.lock(organizationId, technicianId);
        } catch (TechnicianScheduleBusyException e) {
            throw new BusinessException(ScheduleErrorCode.TECHNICIAN_SCHEDULE_BUSY, e);
        }
    }
}
