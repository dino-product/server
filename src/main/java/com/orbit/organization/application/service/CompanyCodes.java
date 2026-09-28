package com.orbit.organization.application.service;

import com.orbit.organization.application.error.OrganizationErrorCode;
import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;
import com.orbit.shared.error.BusinessException;

/** 생성과 변경에 공통으로 쓰는 사용 가능한 회사 코드 발급 정책. */
final class CompanyCodes {
    private static final int MAX_CODE_ATTEMPTS = 5;

    private CompanyCodes() {}

    static CompanyCode nextAvailableCode(CompanyCodeGenerator codes, OrganizationRepository organizations) {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            var code = codes.generate();
            if (!organizations.existsByCode(code)) {
                return code;
            }
        }
        throw new BusinessException(OrganizationErrorCode.COMPANY_CODE_EXHAUSTED);
    }
}
