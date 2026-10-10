package com.orbit.profile.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import com.orbit.auth.AccountPrincipal;
import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.query.CheckServiceAccessUseCase;
import com.orbit.profile.application.port.in.query.dto.CheckServiceAccessQuery;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("가입 완료 공통 검사")
class SignupGateInterceptorTest {

    @Mock
    private CheckServiceAccessUseCase useCase;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("가입을 마친 계정의 컨트롤러 요청은 통과시킨다")
    void passesUsableAccount() {
        authenticate(7L);
        when(useCase.canUseService(new CheckServiceAccessQuery(7L))).thenReturn(true);

        assertThat(preHandle(mock(HandlerMethod.class))).isTrue();
    }

    @Test
    @DisplayName("가입 미완료·재동의 전 계정의 컨트롤러 요청은 PROFILE-005로 거부한다")
    void rejectsAccountThatCannotUseService() {
        authenticate(7L);
        when(useCase.canUseService(new CheckServiceAccessQuery(7L))).thenReturn(false);

        assertThatThrownBy(() -> preHandle(mock(HandlerMethod.class)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.SIGNUP_NOT_COMPLETED));
    }

    @Test
    @DisplayName("컨트롤러로 연결되지 않은 요청(없는 경로)과 인증되지 않은 요청은 검사하지 않는다")
    void skipsNonControllerAndAnonymousRequests() {
        assertThat(preHandle(mock(HandlerMethod.class))).isTrue();

        authenticate(7L);
        assertThat(preHandle(new ResourceHttpRequestHandler())).isTrue();

        verify(useCase, never()).canUseService(any());
    }

    private boolean preHandle(Object handler) {
        return new SignupGateInterceptor(useCase)
                .preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), handler);
    }

    private static void authenticate(long accountId) {
        AccountPrincipal principal = () -> accountId;
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
