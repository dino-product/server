package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;
import com.orbit.profile.application.port.in.query.dto.ProfileInfo;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.profile.domain.SignupStep;
import com.orbit.shared.error.BusinessException;

@ExtendWith(MockitoExtension.class)
@DisplayName("내 정보 조회")
class GetProfileServiceTest {

    @Mock
    private ProfileRepository profiles;

    @InjectMocks
    private GetProfileService service;

    @Test
    @DisplayName("프로필을 입력하지 않은 계정은 가입 미완료이고 다음 단계는 프로필 입력이다")
    void reportsProfileStepWhenAbsent() {
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.empty());

        ProfileInfo info = service.getProfile(new GetProfileQuery(1L, 1L));

        assertThat(info.accountId()).isEqualTo(1L);
        assertThat(info.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
        assertThat(info.nextStep()).isEqualTo(SignupStep.PROFILE);
        assertThat(info.name()).isNull();
        assertThat(info.phoneNumber()).isNull();
    }

    @Test
    @DisplayName("입력해 둔 프로필이 있으면 그 값을 돌려줘 이어서 가입하게 한다")
    void returnsSavedProfileToResume() {
        when(profiles.findByAccountId(new AccountId(1L)))
                .thenReturn(Optional.of(
                        Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"))));

        ProfileInfo info = service.getProfile(new GetProfileQuery(1L, 1L));

        assertThat(info.status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
        assertThat(info.nextStep()).isEqualTo(SignupStep.TERMS);
        assertThat(info.name()).isEqualTo("홍길동");
        assertThat(info.phoneNumber()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("다른 계정의 프로필은 없는 것과 같이 PROFILE-002로 거부한다")
    void rejectsOtherAccount() {
        assertThatThrownBy(() -> service.getProfile(new GetProfileQuery(1L, 2L)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
    }
}
