package com.orbit.notification.application.port.in.query;

import com.orbit.notification.application.port.in.query.dto.CountUnreadNotificationsQuery;

public interface CountUnreadNotificationsUseCase {

    long count(CountUnreadNotificationsQuery query);
}
