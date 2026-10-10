package com.orbit.profile.application.port.in.query;

import com.orbit.profile.application.port.in.query.dto.CheckServiceAccessQuery;

public interface CheckServiceAccessUseCase {

    /** 가입을 마쳤고 시행 중인 필수 약관에 동의한 계정이면 {@code true}. */
    boolean canUseService(CheckServiceAccessQuery query);
}
