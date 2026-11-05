package com.orbit.schedule.application.port.in.command.dto;

/**
 * 완료보고 사진 업로드 주소 발급.
 *
 * @param contentType 올릴 사진의 형식(예: {@code image/jpeg})
 * @param sizeBytes 올릴 사진의 크기(바이트)
 */
public record IssueCompletionPhotoUploadCommand(
        Long accountId, Long organizationId, Long workId, String contentType, Long sizeBytes) {}
