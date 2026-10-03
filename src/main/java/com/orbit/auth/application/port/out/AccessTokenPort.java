package com.orbit.auth.application.port.out;

import java.util.Optional;

import com.orbit.auth.domain.AccessToken;
import com.orbit.auth.domain.AccountId;

public interface AccessTokenPort {

    /** 계정에 대해 새 식별자와 유효 구간을 가진 Access Token을 발급한다. */
    IssuedAccessToken issue(AccountId accountId);

    /** 서명·발급자·시간·토큰 종류가 모두 유효하면 클레임을, 아니면 빈 값을 반환한다. 폐기 여부는 확인하지 않는다. */
    Optional<AccessToken> parse(String value);
}
