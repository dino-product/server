package com.orbit.notification.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.docs.PushDeviceControllerDocs;
import com.orbit.notification.application.port.in.command.RegisterPushDeviceUseCase;
import com.orbit.notification.application.port.in.command.UnregisterPushDeviceUseCase;
import com.orbit.notification.application.port.in.command.dto.RegisterPushDeviceCommand;
import com.orbit.notification.application.port.in.command.dto.UnregisterPushDeviceCommand;

@RestController
@RequestMapping("/api/v1/push-devices")
class PushDeviceController implements PushDeviceControllerDocs {

    private final RegisterPushDeviceUseCase registerPushDeviceUseCase;
    private final UnregisterPushDeviceUseCase unregisterPushDeviceUseCase;

    PushDeviceController(
            RegisterPushDeviceUseCase registerPushDeviceUseCase,
            UnregisterPushDeviceUseCase unregisterPushDeviceUseCase) {
        this.registerPushDeviceUseCase = registerPushDeviceUseCase;
        this.unregisterPushDeviceUseCase = unregisterPushDeviceUseCase;
    }

    @Override
    @PostMapping
    public RegisteredPushDeviceResponse register(
            @AuthenticationPrincipal AccountPrincipal requester, @Valid @RequestBody PushDeviceRequest request) {
        return RegisteredPushDeviceResponse.from(registerPushDeviceUseCase.register(
                new RegisterPushDeviceCommand(requester.accountId(), request.platform(), request.token())));
    }

    @Override
    @DeleteMapping("/{pushDeviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long pushDeviceId) {
        unregisterPushDeviceUseCase.unregister(new UnregisterPushDeviceCommand(requester.accountId(), pushDeviceId));
    }
}
