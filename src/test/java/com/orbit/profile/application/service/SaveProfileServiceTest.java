package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.profile.application.error.ProfileErrorCode;
import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;
import com.orbit.profile.application.port.out.ConcurrentProfileUpdateException;
import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;
import com.orbit.profile.domain.PhoneNumber;
import com.orbit.profile.domain.Profile;
import com.orbit.profile.domain.SignupStatus;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("프로필 입력")
class SaveProfileServiceTest {

    @Mock
    private ProfileRepository profiles;

    @InjectMocks
    private SaveProfileService service;

    @Test
    @DisplayName("프로필이 없으면 가입 미완료 프로필을 만들고 연락처는 숫자만 저장한다")
    void startsProfileWhenAbsent() {
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.empty());

        service.save(new SaveProfileCommand(1L, 1L, " 홍길동 ", "010-1234-5678"));

        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(saved.capture());
        assertThat(saved.getValue().accountId()).isEqualTo(new AccountId(1L));
        assertThat(saved.getValue().name().value()).isEqualTo("홍길동");
        assertThat(saved.getValue().phoneNumber().value()).isEqualTo("01012345678");
        assertThat(saved.getValue().status()).isEqualTo(SignupStatus.PENDING_SIGNUP);
    }

    @Test
    @DisplayName("이미 입력한 프로필이 있으면 이름과 연락처를 바꿔 저장한다")
    void changesExistingProfile() {
        Profile existing = Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"));
        when(profiles.findByAccountId(new AccountId(1L))).thenReturn(Optional.of(existing));

        service.save(new SaveProfileCommand(1L, 1L, "김철수", "01087654321"));

        verify(profiles).save(existing);
        assertThat(existing.name().value()).isEqualTo("김철수");
        assertThat(existing.phoneNumber().value()).isEqualTo("01087654321");
    }

    @Test
    @DisplayName("다른 계정의 프로필은 없는 것과 같이 PROFILE-002로 거부한다")
    void rejectsOtherAccount() {
        assertThatThrownBy(() -> service.save(new SaveProfileCommand(1L, 2L, "홍길동", "01012345678")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.PROFILE_NOT_FOUND));
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("이름이나 연락처 규칙을 어기면 PROFILE-001로 거부하고 저장하지 않는다")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> service.save(new SaveProfileCommand(1L, 1L, "홍", "01012345678")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.INVALID_PROFILE_INPUT));
        assertThatThrownBy(() -> service.save(new SaveProfileCommand(1L, 1L, "홍길동", "0111234567")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ProfileErrorCode.INVALID_PROFILE_INPUT));
        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("다른 요청이 같은 프로필을 먼저 저장했으면 덮어쓰지 않고 COMMON-409로 거부한다")
    void rejectsConcurrentUpdate() {
        when(profiles.findByAccountId(new AccountId(1L)))
                .thenReturn(Optional.of(
                        Profile.start(new AccountId(1L), new PersonName("홍길동"), new PhoneNumber("01012345678"))));
        doThrow(new ConcurrentProfileUpdateException(new AccountId(1L), new RuntimeException("stale")))
                .when(profiles)
                .save(any());

        assertThatThrownBy(() -> service.save(new SaveProfileCommand(1L, 1L, "김철수", "01087654321")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.CONFLICT));
    }
}
