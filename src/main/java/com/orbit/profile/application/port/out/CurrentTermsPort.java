package com.orbit.profile.application.port.out;

import com.orbit.profile.domain.TermsVersions;

public interface CurrentTermsPort {

    /** 지금 시행 중인 약관 버전. */
    TermsVersions currentVersions();
}
