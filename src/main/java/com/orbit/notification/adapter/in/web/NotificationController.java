package com.orbit.notification.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.notification.adapter.in.web.docs.NotificationControllerDocs;
import com.orbit.notification.application.port.in.command.ReadNotificationUseCase;
import com.orbit.notification.application.port.in.command.dto.ReadNotificationCommand;
import com.orbit.notification.application.port.in.query.CountUnreadNotificationsUseCase;
import com.orbit.notification.application.port.in.query.ListNotificationsUseCase;
import com.orbit.notification.application.port.in.query.dto.CountUnreadNotificationsQuery;
import com.orbit.notification.application.port.in.query.dto.ListNotificationsQuery;

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController implements NotificationControllerDocs {

    private final ListNotificationsUseCase listNotificationsUseCase;
    private final CountUnreadNotificationsUseCase countUnreadNotificationsUseCase;
    private final ReadNotificationUseCase readNotificationUseCase;

    NotificationController(
            ListNotificationsUseCase listNotificationsUseCase,
            CountUnreadNotificationsUseCase countUnreadNotificationsUseCase,
            ReadNotificationUseCase readNotificationUseCase) {
        this.listNotificationsUseCase = listNotificationsUseCase;
        this.countUnreadNotificationsUseCase = countUnreadNotificationsUseCase;
        this.readNotificationUseCase = readNotificationUseCase;
    }

    @Override
    @GetMapping
    public NotificationsResponse list(
            @AuthenticationPrincipal AccountPrincipal requester,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return NotificationsResponse.from(listNotificationsUseCase.list(
                new ListNotificationsQuery(requester.accountId(), unreadOnly, page, size)));
    }

    @Override
    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse countUnread(@AuthenticationPrincipal AccountPrincipal requester) {
        return new UnreadNotificationCountResponse(
                countUnreadNotificationsUseCase.count(new CountUnreadNotificationsQuery(requester.accountId())));
    }

    @Override
    @PutMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AccountPrincipal requester, @PathVariable Long notificationId) {
        readNotificationUseCase.read(new ReadNotificationCommand(requester.accountId(), notificationId));
    }
}
