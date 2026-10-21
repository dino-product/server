package com.orbit.organization.application.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.orbit.organization.StaffRegisteredWorks;

/**
 * 임시 {@link StaffRegisteredWorks} 구현. schedule의 실제 구현 전에는 작업을 확인하지 못하므로 언제나 끝나지 않은 작업이 있다고 답해 직원을 기사로 바꾸는 역할
 * 변경을 거부한다. schedule이 이 요구 인터페이스를 구현하는 PR에서 삭제한다. 로컬·테스트·개발 서버 프로필에서만 등록한다.
 */
@Service
@Profile({"local & !prod", "test & !prod", "dev & !prod"})
class DenyingStaffRegisteredWorks implements StaffRegisteredWorks {

    @Override
    public boolean existsUnfinished(Long organizationId, Long membershipId) {
        return true;
    }
}
