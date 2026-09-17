package com.example.user.application.service;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.user.UserRegistered;
import com.example.user.application.port.in.command.RegisterUserUseCase;
import com.example.user.application.port.in.command.dto.RegisterUserCommand;
import com.example.user.application.port.in.command.dto.RegisteredUserInfo;
import com.example.user.application.port.out.UserRepository;
import com.example.user.domain.User;
import com.example.user.domain.UserId;

@Service
public class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterUserService(UserRepository userRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RegisteredUserInfo register(RegisterUserCommand command) {
        User savedUser = userRepository.save(User.register(command.displayName()));
        UserId userId = savedUser.id().orElseThrow(() -> new IllegalStateException("saved user must have an id"));

        eventPublisher.publishEvent(new UserRegistered(userId.value(), clock.instant()));
        return new RegisteredUserInfo(userId.value(), savedUser.displayName());
    }
}
