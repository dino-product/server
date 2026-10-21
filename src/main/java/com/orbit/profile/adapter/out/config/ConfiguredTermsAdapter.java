package com.orbit.profile.adapter.out.config;

import org.springframework.stereotype.Component;

import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.domain.TermsVersions;

/** 약관 버전은 설정값으로 관리한다. 값이 비면 {@link TermsVersions}가 거부해 기동에 실패한다. */
@Component
class ConfiguredTermsAdapter implements CurrentTermsPort {

    private final TermsVersions versions;

    ConfiguredTermsAdapter(TermsVersionProperties properties) {
        this.versions = new TermsVersions(properties.service(), properties.privacy(), properties.marketing());
    }

    @Override
    public TermsVersions currentVersions() {
        return versions;
    }
}
