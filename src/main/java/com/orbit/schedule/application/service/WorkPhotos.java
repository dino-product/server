package com.orbit.schedule.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.orbit.media.AttachmentConflictException;
import com.orbit.media.MediaFiles;
import com.orbit.media.MediaStorageUnavailableException;
import com.orbit.media.StageRequest;
import com.orbit.media.StagedFiles;
import com.orbit.media.UploadNotAllowedException;
import com.orbit.media.UploadRequest;
import com.orbit.media.UploadSpec;
import com.orbit.media.UploadTicket;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkId;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 사진(현장 사진·완료보고 사진)을 media 모듈로 올리고 붙이는 공통 절차(ADR-006). 사진을 받는 유즈케이스는 업무 트랜잭션을 열기 전에 {@link #stage}로 저장소 확인을
 * 끝내고, 트랜잭션 안의 입력 확인 자리에서 {@link #requireAccepted}로 확인 결과를 알린 뒤, 작업을 저장한 같은 트랜잭션에서 {@link #attach}한다. 연결 대상은
 * {@code work:{workId}}이고 용도로 현장 사진과 완료보고 사진을 나눈다.
 */
final class WorkPhotos {

    static final String SITE_PHOTO = "WORK_SITE_PHOTO";
    static final String COMPLETION_PHOTO = "WORK_COMPLETION_PHOTO";

    private WorkPhotos() {}

    /**
     * 트랜잭션 밖에서 사진을 확인·복사한다. 조직·작업 식별자는 아직 확인하지 않은 요청 값이어도 되며(맞지 않으면 사진이 거부될 뿐), 사진이 없으면 저장소를 부르지 않는다.
     * 저장소에 닿지 못하면 503(SCHEDULE-023)이다.
     *
     * @param workIdValue 이미 사진이 붙어 있을 수 있는 작업(기본정보 수정·완료보고 제출), 새 작업이면 null
     */
    static StagedFiles stage(
            MediaFiles mediaFiles,
            Long accountId,
            Long organizationIdValue,
            String purpose,
            List<String> photoIds,
            Long workIdValue) {
        // 빈 목록은 저장소를 부를 일이 없고, null 항목은 트랜잭션 안의 입력 확인이 400으로 거부한다.
        if (photoIds == null || photoIds.isEmpty() || photoIds.stream().anyMatch(Objects::isNull)) {
            return StagedFiles.empty(organizationIdValue, accountId, purpose);
        }
        String owner = workIdValue == null || workIdValue <= 0 ? null : owner(new WorkId(workIdValue));
        try {
            return mediaFiles.stage(new StageRequest(organizationIdValue, accountId, purpose, photoIds, owner));
        } catch (MediaStorageUnavailableException exception) {
            throw new BusinessException(ScheduleErrorCode.PHOTO_STORAGE_UNAVAILABLE);
        }
    }

    /** 확인에 실패한 사진이 있으면 그 사유의 오류. 다른 입력 확인과 같은 자리(입력 형식 400)에서 부른다. */
    static void requireAccepted(StagedFiles staged) {
        staged.findRejection().ifPresent(rejection -> {
            throw new BusinessException(
                    switch (rejection.reason()) {
                        case DUPLICATED -> ScheduleErrorCode.INVALID_WORK_INPUT;
                        case NOT_UPLOADED -> ScheduleErrorCode.PHOTO_NOT_UPLOADED;
                        case CONTENT_TYPE_MISMATCH -> ScheduleErrorCode.PHOTO_TYPE_NOT_ALLOWED;
                        case TOO_LARGE -> ScheduleErrorCode.PHOTO_TOO_LARGE;
                    });
        });
    }

    /** 확인한 사진을 저장한 작업의 그 용도 사진 전체로 확정한다. 작업을 저장한 같은 트랜잭션에서 부른다. 확인 뒤 다른 요청이 그 사진을 먼저 썼으면 400(SCHEDULE-022)이다. */
    static void attach(MediaFiles mediaFiles, StagedFiles staged, WorkId workId) {
        try {
            mediaFiles.attach(staged, owner(workId));
        } catch (AttachmentConflictException exception) {
            throw new BusinessException(ScheduleErrorCode.PHOTO_NOT_UPLOADED);
        }
    }

    /** 업로드 주소를 발급한다. 형식이 이미지가 아니면 400(SCHEDULE-020), 크기가 상한을 넘으면 400(SCHEDULE-021)이다. */
    static PhotoUploadInfo issue(
            MediaFiles mediaFiles,
            OrganizationId organizationId,
            Long accountId,
            String purpose,
            long maxBytes,
            String contentType,
            long sizeBytes) {
        try {
            UploadTicket ticket = mediaFiles.issueUpload(new UploadRequest(
                    organizationId.value(), accountId, new UploadSpec(purpose, maxBytes), contentType, sizeBytes));
            return new PhotoUploadInfo(
                    ticket.fileId(), ticket.uploadUrl(), ticket.requiredHeaders(), ticket.expiresAt());
        } catch (UploadNotAllowedException exception) {
            throw new BusinessException(
                    switch (exception.reason()) {
                        case CONTENT_TYPE_NOT_ALLOWED -> ScheduleErrorCode.PHOTO_TYPE_NOT_ALLOWED;
                        case SIZE_NOT_ALLOWED -> ScheduleErrorCode.PHOTO_TOO_LARGE;
                    });
        }
    }

    /** 업로드 주소 발급 요청 값 형식(형식 문자열·1바이트 이상 크기). 아니면 400(SCHEDULE-003)이다. */
    static void requireUploadInput(String contentType, Long sizeBytes) {
        if (contentType == null || contentType.isBlank() || sizeBytes == null || sizeBytes < 1) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    /** 완료보고의 작업 전·후 사진을 한 목록으로(같은 사진을 양쪽에 두면 중복으로 거부된다). */
    static List<String> completionPhotos(List<String> beforePhotos, List<String> afterPhotos) {
        List<String> photos = new ArrayList<>();
        if (beforePhotos != null) {
            photos.addAll(beforePhotos);
        }
        if (afterPhotos != null) {
            photos.addAll(afterPhotos);
        }
        return photos;
    }

    private static String owner(WorkId workId) {
        return "work:" + workId.value();
    }
}
