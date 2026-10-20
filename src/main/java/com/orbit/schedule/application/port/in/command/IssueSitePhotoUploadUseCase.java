package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.IssueSitePhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;

public interface IssueSitePhotoUploadUseCase {

    PhotoUploadInfo issue(IssueSitePhotoUploadCommand command);
}
