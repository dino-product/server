package com.orbit.schedule.adapter.in.web.photo;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.orbit.auth.AccountPrincipal;
import com.orbit.schedule.adapter.in.web.docs.PhotoUploadControllerDocs;
import com.orbit.schedule.application.port.in.command.IssueCompletionPhotoUploadUseCase;
import com.orbit.schedule.application.port.in.command.IssueSitePhotoUploadUseCase;
import com.orbit.schedule.application.port.in.command.dto.IssueCompletionPhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.IssueSitePhotoUploadCommand;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}")
class PhotoUploadController implements PhotoUploadControllerDocs {

    private final IssueSitePhotoUploadUseCase issueSitePhotoUploadUseCase;
    private final IssueCompletionPhotoUploadUseCase issueCompletionPhotoUploadUseCase;

    PhotoUploadController(
            IssueSitePhotoUploadUseCase issueSitePhotoUploadUseCase,
            IssueCompletionPhotoUploadUseCase issueCompletionPhotoUploadUseCase) {
        this.issueSitePhotoUploadUseCase = issueSitePhotoUploadUseCase;
        this.issueCompletionPhotoUploadUseCase = issueCompletionPhotoUploadUseCase;
    }

    @Override
    @PostMapping("/site-photo-uploads")
    public PhotoUploadResponse issueSitePhotoUpload(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @Valid @RequestBody PhotoUploadRequest request) {
        return PhotoUploadResponse.from(issueSitePhotoUploadUseCase.issue(new IssueSitePhotoUploadCommand(
                requester.accountId(), organizationId, request.contentType(), request.sizeBytes())));
    }

    @Override
    @PostMapping("/works/{workId}/completion-photo-uploads")
    public PhotoUploadResponse issueCompletionPhotoUpload(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            @PathVariable Long workId,
            @Valid @RequestBody PhotoUploadRequest request) {
        return PhotoUploadResponse.from(issueCompletionPhotoUploadUseCase.issue(new IssueCompletionPhotoUploadCommand(
                requester.accountId(), organizationId, workId, request.contentType(), request.sizeBytes())));
    }
}
