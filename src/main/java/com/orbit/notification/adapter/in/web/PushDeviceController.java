package com.orbit.notification.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.docs.PushDeviceControllerDocs;
import com.orbit.notification.application.port.in.command.RegisterPushDeviceUseCase;
import com.orbit.notification.application.port.in.command.dto.RegisterPushDeviceCommand;

@RestController
@RequestMapping("/api/v1/push-devices")
class PushDeviceController implements PushDeviceControllerDocs {

    private final RegisterPushDeviceUseCase registerPushDeviceUseCase;

    PushDeviceController(RegisterPushDeviceUseCase registerPushDeviceUseCase) {
        this.registerPushDeviceUseCase = registerPushDeviceUseCase;
    }

    @Override
    @PostMapping
    public RegisteredPushDeviceResponse register(
            @AuthenticationPrincipal AccountPrincipal requester, @Valid @RequestBody PushDeviceRequest request) {
        return RegisteredPushDeviceResponse.from(registerPushDeviceUseCase.register(
                new RegisterPushDeviceCommand(requester.accountId(), request.platform(), request.token())));
    }
}
