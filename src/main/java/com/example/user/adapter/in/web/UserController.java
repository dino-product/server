package com.example.user.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.user.adapter.in.web.docs.UserControllerDocs;
import com.example.user.application.port.in.command.RegisterUserUseCase;
import com.example.user.application.port.in.command.dto.RegisterUserCommand;

@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserControllerDocs {

    private final RegisterUserUseCase useCase;

    public UserController(RegisterUserUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    @PostMapping
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        return UserResponse.from(useCase.register(new RegisterUserCommand(request.displayName())));
    }
}
