package com.orbit.notification.application.port.in.query;

import com.orbit.notification.application.port.in.query.dto.GetNotificationSettingQuery;
import com.orbit.notification.application.port.in.query.dto.NotificationSettingInfo;

public interface GetNotificationSettingUseCase {

    NotificationSettingInfo get(GetNotificationSettingQuery query);
}
