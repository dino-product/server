package com.orbit.organization.adapter.out.code;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.orbit.organization.application.port.out.CompanyCodeGenerator;
import com.orbit.organization.domain.CompanyCode;

/** 참여 경로가 추측되지 않도록 {@link SecureRandom}으로 회사 코드 문자를 하나씩 고른다. */
@Component
class SecureRandomCompanyCodeGenerator implements CompanyCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public CompanyCode generate() {
        StringBuilder value = new StringBuilder(CompanyCode.LENGTH);
        for (int i = 0; i < CompanyCode.LENGTH; i++) {
            value.append(CompanyCode.ALPHABET.charAt(random.nextInt(CompanyCode.ALPHABET.length())));
        }
        return new CompanyCode(value.toString());
    }
}
