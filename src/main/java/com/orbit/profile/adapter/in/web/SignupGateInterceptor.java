package com.orbit.profile.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.orbit.auth.AccountPrincipal;
import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.query.CheckServiceAccessUseCase;
import com.orbit.profile.application.port.in.query.dto.CheckServiceAccessQuery;
import com.orbit.shared.error.BusinessException;

/**
 * 가입 미완료 계정과 필수 약관 재동의 전 계정이 가입을 마치는 데 필요한 API 밖을 쓰지 못하게 막는다([조직·계정] 정책 §5 가입 미완료 계정의 허용 범위). 검사 대상
 * 경로와 허용 목록은 {@link ProfileWebConfig}가 정한다.
 *
 * <p>컨트롤러 메서드로 연결된 요청만 검사한다. 없는 경로는 정적 자원 처리기로 넘어가 그대로 404가 되므로, 폐기·만료 토큰의 404가 실제로 없는 자원의 404와 같아야
 * 한다는 auth 계약을 깨지 않는다. 인증되지 않은 요청은 인가 단계가 이미 걸렀거나 공개 경로이므로 넘긴다.
 */
class SignupGateInterceptor implements HandlerInterceptor {

    private final CheckServiceAccessUseCase useCase;

    SignupGateInterceptor(CheckServiceAccessUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            return true;
        }
        if (!useCase.canUseService(new CheckServiceAccessQuery(principal.accountId()))) {
            throw new BusinessException(ProfileErrorCode.SIGNUP_NOT_COMPLETED);
        }
        return true;
    }
}
