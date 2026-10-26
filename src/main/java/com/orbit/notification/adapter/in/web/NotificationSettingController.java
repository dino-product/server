package com.orbit.notification.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.docs.NotificationSettingControllerDocs;
import com.orbit.notification.application.port.in.command.ChangeNotificationSettingUseCase;
import com.orbit.notification.application.port.in.command.dto.ChangeNotificationSettingCommand;
import com.orbit.notification.application.port.in.query.GetNotificationSettingUseCase;
import com.orbit.notification.application.port.in.query.dto.GetNotificationSettingQuery;

@RestController
@RequestMapping("/api/v1/notification-settings")
class NotificationSettingController implements NotificationSettingControllerDocs {

    private final GetNotificationSettingUseCase getNotificationSettingUseCase;
    private final ChangeNotificationSettingUseCase changeNotificationSettingUseCase;

    NotificationSettingController(
            GetNotificationSettingUseCase getNotificationSettingUseCase,
            ChangeNotificationSettingUseCase changeNotificationSettingUseCase) {
        this.getNotificationSettingUseCase = getNotificationSettingUseCase;
        this.changeNotificationSettingUseCase = changeNotificationSettingUseCase;
    }

    @Override
    @GetMapping
    public NotificationSettingResponse get(@AuthenticationPrincipal AccountPrincipal requester) {
        return NotificationSettingResponse.from(
                getNotificationSettingUseCase.get(new GetNotificationSettingQuery(requester.accountId())));
    }

    @Override
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(
            @AuthenticationPrincipal AccountPrincipal requester,
            @Valid @RequestBody NotificationSettingRequest request) {
        changeNotificationSettingUseCase.change(new ChangeNotificationSettingCommand(
                requester.accountId(),
                request.workAssignmentEnabled(),
                request.scheduleChangeEnabled(),
                request.workCancellationEnabled()));
    }
}
