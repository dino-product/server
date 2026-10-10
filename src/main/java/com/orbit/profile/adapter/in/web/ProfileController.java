package com.orbit.profile.adapter.in.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.profile.adapter.in.web.docs.ProfileControllerDocs;
import com.orbit.profile.application.port.in.command.SaveProfileUseCase;
import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;
import com.orbit.profile.application.port.in.query.GetProfileUseCase;
import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;

/** 변경 API도 저장 뒤의 가입 단계를 같은 모양으로 돌려줘 클라이언트가 다음 화면을 바로 고르게 한다. */
@Validated
@RestController
@RequestMapping("/api/v1/profiles")
class ProfileController implements ProfileControllerDocs {

    private final GetProfileUseCase getProfileUseCase;
    private final SaveProfileUseCase saveProfileUseCase;

    ProfileController(GetProfileUseCase getProfileUseCase, SaveProfileUseCase saveProfileUseCase) {
        this.getProfileUseCase = getProfileUseCase;
        this.saveProfileUseCase = saveProfileUseCase;
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

    private ProfileResponse read(AccountPrincipal requester, Long accountId) {
        return ProfileResponse.from(
                getProfileUseCase.getProfile(new GetProfileQuery(requester.accountId(), accountId)));
    }
}
