package com.orbit.organization.application.service;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.CompanyCodeAttemptLimiter;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.AccountId;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.organization.domain.Organization;
import com.orbit.shared.error.BusinessException;

/** 사용자가 입력한 회사 코드로 발주사를 찾는다. 회사 확인과 참여 요청이 같은 판정과 실패 횟수를 쓴다. */
final class CompanyCodes {

    private CompanyCodes() {}

    /**
     * 입력을 정규화해 현재 코드가 같은 발주사를 돌려준다. 형식이 틀린 입력은 어느 코드와도 같을 수 없으므로 없는 코드와 같게 거부하고, 폐기된 이전 코드는 따로
     * 알린다. 실패가 한도에 이른 계정은 코드를 찾아보기 전에 거부해 막힌 동안에도 코드의 존재 여부를 드러내지 않는다.
     */
    static Organization resolve(
            OrganizationRepository organizationRepository,
            CompanyCodeAttemptLimiter attemptLimiter,
            AccountId requester,
            String input) {
        if (attemptLimiter.isBlocked(requester)) {
            throw new BusinessException(OrganizationErrorCode.TOO_MANY_COMPANY_CODE_ATTEMPTS);
        }
        CompanyCode code;
        try {
            code = CompanyCode.parse(input);
        } catch (IllegalArgumentException exception) {
            attemptLimiter.recordFailure(requester);
            throw new BusinessException(OrganizationErrorCode.COMPANY_NOT_FOUND, exception);
        }
        return organizationRepository.findByCode(code).orElseThrow(() -> {
            if (organizationRepository.existsRetiredCode(code)) {
                return new BusinessException(OrganizationErrorCode.RETIRED_COMPANY_CODE);
            }
            attemptLimiter.recordFailure(requester);
            return new BusinessException(OrganizationErrorCode.COMPANY_NOT_FOUND);
        });
    }
}
