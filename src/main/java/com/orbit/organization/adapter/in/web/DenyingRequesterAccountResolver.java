package com.orbit.organization.adapter.in.web;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/**
 * 임시 {@link RequesterAccountResolver} 구현. auth의 인증 계정 공개 계약(HM-296)이 생기면 그 계약을 쓰도록 바꾼 뒤 삭제한다. 그 전까지는 어떤
 * 요청자도 확인하지 않아(안전한 기본 동작) 모든 요청이 권한 없음으로 끝난다. {@code local}·{@code test} 프로필에서만 등록한다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class DenyingRequesterAccountResolver implements RequesterAccountResolver {

    @Override
    public Long resolve() {
        throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}
