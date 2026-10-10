package com.orbit.profile.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.profile.adapter.in.web.docs.ProfileControllerDocs;
import com.orbit.profile.application.port.in.command.AgreeToTermsUseCase;
import com.orbit.profile.application.port.in.command.SaveProfileUseCase;
import com.orbit.profile.application.port.in.command.dto.AgreeToTermsCommand;
import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;
import com.orbit.profile.application.port.in.query.GetProfileUseCase;
import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;

/**
 * 변경 API도 저장 뒤의 가입 단계를 같은 모양으로 돌려줘 클라이언트가 다음 화면을 바로 고르게 한다.
 *
 * <p>경로 변수 제약 때문에 {@code @Validated}로 메서드 검증을 켜므로, 구현 메서드에만 {@code @Valid}를 붙이면 Docs 인터페이스의 매개변수 제약을 다시
 * 정의한 것이 되어 Bean Validation이 모든 호출을 거부한다(HV000151). 요청 본문 규칙은 도메인이 검사하므로 본문에는 {@code @Valid}를 두지 않는다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController implements ProfileControllerDocs {

    private final GetProfileUseCase getProfileUseCase;
    private final SaveProfileUseCase saveProfileUseCase;
    private final AgreeToTermsUseCase agreeToTermsUseCase;

    ProfileController(
            GetProfileUseCase getProfileUseCase,
            SaveProfileUseCase saveProfileUseCase,
            AgreeToTermsUseCase agreeToTermsUseCase) {
        this.getProfileUseCase = getProfileUseCase;
        this.saveProfileUseCase = saveProfileUseCase;
        this.agreeToTermsUseCase = agreeToTermsUseCase;
    }

    @Override
    @GetMapping("/{accountId}")
    public ProfileResponse getProfile(
            @AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long accountId) {
        return read(requester, accountId);
    }

    @Override
    @PutMapping("/{accountId}")
    public ProfileResponse saveProfile(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long accountId,
            @RequestBody SaveProfileRequest request) {
        saveProfileUseCase.save(
                new SaveProfileCommand(requester.accountId(), accountId, request.name(), request.phoneNumber()));
        return read(requester, accountId);
    }

    @Override
    @PostMapping("/{accountId}/terms-agreements")
    public ProfileResponse agreeToTerms(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long accountId,
            @RequestBody AgreeToTermsRequest request) {
        agreeToTermsUseCase.agree(new AgreeToTermsCommand(
                requester.accountId(),
                accountId,
                Boolean.TRUE.equals(request.serviceTerms()),
                Boolean.TRUE.equals(request.privacyPolicy()),
                request.marketing()));
        return read(requester, accountId);
    }

    private ProfileResponse read(AccountPrincipal requester, Long accountId) {
        return ProfileResponse.from(
                getProfileUseCase.getProfile(new GetProfileQuery(requester.accountId(), accountId)));
    }
}
