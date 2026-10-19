package com.orbit.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.profile.application.port.in.command.SaveProfileUseCase;
import com.orbit.profile.application.port.in.command.dto.SaveProfileCommand;
import com.orbit.profile.application.port.in.query.GetProfileUseCase;
import com.orbit.profile.application.port.in.query.dto.GetProfileQuery;
import com.orbit.profile.domain.SignupStep;
import com.orbit.support.TestcontainersConfiguration;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class ProfileModuleTest {

    @Autowired
    private SaveProfileUseCase saveProfile;

    @Autowired
    private GetProfileUseCase getProfile;

    @Autowired
    private ProfileLookup profileLookup;

    @Test
    void savesAndReadsProfileWithinModule() {
        saveProfile.save(new SaveProfileCommand(77L, 77L, "홍길동", "01012345678"));

        assertThat(getProfile.getProfile(new GetProfileQuery(77L, 77L)).nextStep())
                .isEqualTo(SignupStep.TERMS);
    }

    @Test
    void exposesNameLookupForSignedUpAccountsOnly() {
        saveProfile.save(new SaveProfileCommand(78L, 78L, "김철수", "01012345678"));

        assertThat(profileLookup.findNames(Set.of(78L))).isEmpty();
    }
}
