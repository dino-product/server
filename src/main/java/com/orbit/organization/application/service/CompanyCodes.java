package com.orbit.organization.application.service;

import java.util.Optional;

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
     * 알린다. 시도를 코드를 찾기 전에 세어 한도를 넘은 계정은 막힌 동안에도 코드의 존재 여부를 알 수 없고, 동시에 몰아 보내도 한도만큼만 찾아본다.
     */
    static Organization resolve(
            OrganizationRepository organizationRepository,
            CompanyCodeAttemptLimiter attemptLimiter,
            AccountId requester,
            String input) {
        if (!attemptLimiter.tryAttempt(requester)) {
            throw new BusinessException(OrganizationErrorCode.TOO_MANY_COMPANY_CODE_ATTEMPTS);
        }
        CompanyCode code;
        try {
            code = CompanyCode.parse(input);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(OrganizationErrorCode.COMPANY_NOT_FOUND, exception);
        }
        Optional<Organization> organization = organizationRepository.findByCode(code);
        if (organization.isPresent()) {
            attemptLimiter.refund(requester);
            return organization.get();
        }
        if (organizationRepository.existsRetiredCode(code)) {
            attemptLimiter.refund(requester);
            throw new BusinessException(OrganizationErrorCode.RETIRED_COMPANY_CODE);
        }
        throw new BusinessException(OrganizationErrorCode.COMPANY_NOT_FOUND);
    }
}
