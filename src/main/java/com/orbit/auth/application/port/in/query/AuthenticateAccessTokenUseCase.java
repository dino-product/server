package com.orbit.auth.application.port.in.query;

import java.util.Optional;

import com.orbit.auth.application.port.in.query.dto.AccessTokenInfo;
import com.orbit.auth.application.port.in.query.dto.AuthenticateAccessTokenQuery;

public interface AuthenticateAccessTokenUseCase {

    /** 서명·발급자·만료·용도가 유효하고 폐기되지 않은 토큰이면 정보를, 아니면 빈 값을 돌려준다. 실패 이유는 구분하지 않는다. */
    Optional<AccessTokenInfo> authenticate(AuthenticateAccessTokenQuery query);
}
