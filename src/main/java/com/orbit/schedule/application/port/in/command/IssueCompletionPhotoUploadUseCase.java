package com.orbit.schedule.application.port.in.command;

import com.orbit.schedule.application.port.in.command.dto.IssueCompletionPhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;

public interface IssueCompletionPhotoUploadUseCase {

    PhotoUploadInfo issue(IssueCompletionPhotoUploadCommand command);
}
