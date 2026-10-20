package com.orbit.notification.application.port.in.query;

import com.orbit.notification.application.port.in.query.dto.ListNotificationsQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationPageInfo;

public interface ListNotificationsUseCase {

    NotificationPageInfo list(ListNotificationsQuery query);
}
