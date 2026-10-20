package com.orbit.schedule.application.port.in.command.dto;

/**
 * 현장 사진 업로드 주소 발급. 작업 등록 전에도 올리므로 작업 식별자가 없다.
 *
 * @param contentType 올릴 사진의 형식(예: {@code image/jpeg})
 * @param sizeBytes 올릴 사진의 크기(바이트)
 */
public record IssueSitePhotoUploadCommand(Long accountId, Long organizationId, String contentType, Long sizeBytes) {}
