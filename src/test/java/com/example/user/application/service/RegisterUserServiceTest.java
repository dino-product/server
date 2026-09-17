package com.example.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.user.UserRegistered;
import com.example.user.application.port.in.command.dto.RegisterUserCommand;
import com.example.user.application.port.in.command.dto.RegisteredUserInfo;
import com.example.user.application.port.out.UserRepository;
import com.example.user.domain.User;
import com.example.user.domain.UserId;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RegisterUserService service;

    @BeforeEach
    void setUp() {
        service = new RegisterUserService(userRepository, eventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersUserAndPublishesEvent() {
        when(userRepository.save(any(User.class))).thenReturn(User.reconstitute(new UserId(1L), "홍길동"));

        RegisteredUserInfo result = service.register(new RegisterUserCommand("홍길동"));

        assertThat(result).isEqualTo(new RegisteredUserInfo(1L, "홍길동"));
        verify(userRepository).save(any(User.class));

        ArgumentCaptor<UserRegistered> eventCaptor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new UserRegistered(1L, NOW));
    }
}
