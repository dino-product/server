package com.orbit.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.support.TestcontainersConfiguration;
import com.orbit.user.application.port.in.command.RegisterUserUseCase;
import com.orbit.user.application.port.in.command.dto.RegisterUserCommand;
import com.orbit.user.application.port.in.command.dto.RegisteredUserInfo;

@ApplicationModuleTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class UserModuleTest {

    @Autowired
    private RegisterUserUseCase useCase;

    @Test
    void registersUserAndPublishesPublicEvent(AssertablePublishedEvents events) {
        RegisteredUserInfo user = useCase.register(new RegisterUserCommand("모듈 사용자"));

        assertThat(user.id()).isPositive();
        assertThat(events.ofType(UserRegistered.class)).singleElement().satisfies(event -> assertThat(event.userId())
                .isEqualTo(user.id()));
    }
}
