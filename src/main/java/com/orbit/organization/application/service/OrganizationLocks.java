package com.orbit.organization.application.service;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.OrganizationBusyException;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.shared.error.BusinessException;

/** 같은 발주사의 변경을 한 줄로 세우는 공통 절차. 대기 한도를 넘기면 409(ORGANIZATION-010)로 바꾸고, 트랜잭션은 예외로 롤백된다. */
final class OrganizationLocks {

    private OrganizationLocks() {}

    static void lock(OrganizationRepository repository, OrganizationId organizationId) {
        try {
            repository.lock(organizationId);
        } catch (OrganizationBusyException exception) {
            throw new BusinessException(OrganizationErrorCode.ORGANIZATION_BUSY, exception);
        }
    }
}
