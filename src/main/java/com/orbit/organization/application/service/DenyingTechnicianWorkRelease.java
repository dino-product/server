package com.orbit.organization.application.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.orbit.organization.TechnicianWorkRelease;
import com.orbit.organization.TechnicianWorkReleaseResult;

/**
 * 임시 TechnicianWorkRelease 구현. schedule의 실제 구현(HM-252) 전에는 작업을 돌리지 않은 채 성공한 척하지 않도록 모든 호출을 거부한다. schedule이 이
 * 요구 인터페이스를 구현하는 PR에서 삭제한다. {@code local}·{@code test} 프로필에서만 등록한다.
 */
@Service
@Profile({"local & !prod", "test & !prod"})
class DenyingTechnicianWorkRelease implements TechnicianWorkRelease {

    private static final String MESSAGE = "schedule의 대기함 반환 구현(HM-252) 전에는 지원하지 않는다";

    @Override
    public TechnicianWorkReleaseResult releaseForRoleChange(
            Long organizationId, Long technicianId, Long processedByMembershipId) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public void releaseForDeactivation(Long organizationId, Long technicianId, Long processedByMembershipId) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
