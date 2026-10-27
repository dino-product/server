package com.orbit.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.auth.AccountClosure;
import com.orbit.profile.application.port.in.command.SaveProfileUseCase;
import com.orbit.profile.application.port.in.command.WithdrawUseCase;
import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;
import com.orbit.profile.application.port.in.command.dto.WithdrawCommand;
import com.orbit.profile.application.port.in.query.GetProfileUseCase;
import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;
import com.orbit.profile.domain.SignupStep;
import com.orbit.support.SharedContainers;

@ApplicationModuleTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProfileModuleTest implements SharedContainers {

    @Autowired
    private SaveProfileUseCase saveProfile;

    @Autowired
    private GetProfileUseCase getProfile;

    @Autowired
    private WithdrawUseCase withdraw;

    /** auth는 이 모듈 테스트에 조립되지 않으므로 공개 계약을 대신한다. */
    @MockitoBean
    private AccountClosure accountClosure;

    @Autowired
    private ProfileLookup profileLookup;

    @Test
    void savesAndReadsProfileWithinModule() {
        saveProfile.save(new SaveProfileCommand(77L, 77L, "홍길동", "01012345678"));

        assertThat(getProfile.getProfile(new GetProfileQuery(77L, 77L)).nextStep())
                .isEqualTo(SignupStep.TERMS);
    }

    @Test
    void withdrawsThroughAuthAccountClosureContract() {
        saveProfile.save(new SaveProfileCommand(79L, 79L, "홍길동", "01012345678"));

        withdraw.withdraw(new WithdrawCommand(79L, 79L, null));

        verify(accountClosure).close(79L);
    }

    @Test
    void exposesNameLookupForSignedUpAccountsOnly() {
        saveProfile.save(new SaveProfileCommand(78L, 78L, "김철수", "01012345678"));

        assertThat(profileLookup.findNames(Set.of(78L))).isEmpty();
    }
}
