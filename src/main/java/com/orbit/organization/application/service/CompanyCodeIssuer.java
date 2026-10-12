package com.orbit.organization.application.service;

import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.application.port.out.OrganizationRepository;
import com.orbit.organization.domain.CompanyCode;

/**
 * 발주사 생성과 회사 코드 변경이 함께 쓰는 회사 코드 발급. 현재 코드·폐기 코드와 겹치지 않을 때까지 후보를 다시 만든다. 사전 확인과 저장 사이의 동시 발급은
 * {@code companies.code} 유일 제약이 막으며, 이 경우 요청은 실패하고 클라이언트가 다시 시도한다. 32^6 후보 공간에서 겹칠 확률이 매우 낮아 재시도 횟수를 작게
 * 둔다.
 */
final class CompanyCodeIssuer {

    static final int MAX_ATTEMPTS = 5;

    private CompanyCodeIssuer() {}

    static CompanyCode issue(OrganizationRepository organizationRepository, CompanyCodeGenerator codeGenerator) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            CompanyCode candidate = codeGenerator.generate();
            if (!organizationRepository.existsIssuedCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not issue a unique company code");
    }
}
