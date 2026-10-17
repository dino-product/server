package com.orbit.profile.application.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.SaveProfileUseCase;
import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

/** 프로필이 없으면 가입 미완료 프로필을 만들고, 있으면 이름·연락처를 바꾼다. 가입 상태는 바꾸지 않는다. */
@Service
public class SaveProfileService implements SaveProfileUseCase {

    private final ProfileRepository profiles;

    public SaveProfileService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional
    public void save(SaveProfileCommand command) {
        AccountId accountId = ProfileOwnership.requireSelf(command.requesterAccountId(), command.accountId());
        PersonName name = validInput(() -> new PersonName(command.name()));
        PhoneNumber phoneNumber = validInput(() -> new PhoneNumber(command.phoneNumber()));
        Profile profile = profiles.findByAccountId(accountId)
                .map(existing -> {
                    existing.changeBasics(name, phoneNumber);
                    return existing;
                })
                .orElseGet(() -> Profile.start(accountId, name, phoneNumber));
        try {
            profiles.save(profile);
        } catch (ConcurrentProfileUpdateException exception) {
            throw new BusinessException(CommonErrorCode.CONFLICT, exception);
        }
    }

    private static <T> T validInput(Supplier<T> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ProfileErrorCode.INVALID_PROFILE_INPUT, exception);
        }
    }
}
