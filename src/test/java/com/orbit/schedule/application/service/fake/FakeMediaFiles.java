package com.orbit.schedule.application.service.fake;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.orbit.media.AttachmentConflictException;
import com.orbit.media.MediaFiles;
import com.orbit.media.MediaStorageUnavailableException;
import com.orbit.media.StageRequest;
import com.orbit.media.StagedFiles;
import com.orbit.media.StagedFiles.FileRejection;
import com.orbit.media.StagedFiles.Reason;
import com.orbit.media.UploadNotAllowedException;
import com.orbit.media.UploadRequest;
import com.orbit.media.UploadTicket;

/**
 * media 공개 계약의 fake. 테스트가 거부·장애를 넣지 않으면 모든 사진을 받아들이고 호출을 기록한다. 계약의 반환 형태와 계약이 정한 실패(확인 거부, 저장소 장애, 서명
 * 실패, 연결 충돌, 발급 거부)만 흉내 내고 media의 확인 규칙은 구현하지 않는다.
 */
public class FakeMediaFiles implements MediaFiles {

    public static final Instant EXPIRES_AT = Instant.parse("2026-09-24T01:10:00Z");

    /** 기록한 연결. */
    public record Attachment(StagedFiles staged, String owner) {}

    private final Map<String, Reason> rejections = new HashMap<>();
    private final List<StageRequest> stageRequests = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();
    private final List<UploadRequest> uploadRequests = new ArrayList<>();
    private boolean storageDown;
    private boolean signingFails;
    private boolean attachConflict;
    private UploadNotAllowedException.Reason uploadRejection;

    public void givenRejected(String fileId, Reason reason) {
        rejections.put(fileId, reason);
    }

    public void givenStorageDown() {
        storageDown = true;
    }

    /** 업로드·내려받기 주소를 서명하지 못함(자격 증명을 얻지 못함 등). */
    public void givenSigningFails() {
        signingFails = true;
    }

    public void givenAttachConflict() {
        attachConflict = true;
    }

    public void givenUploadRejected(UploadNotAllowedException.Reason reason) {
        uploadRejection = reason;
    }

    public List<StageRequest> stageRequests() {
        return List.copyOf(stageRequests);
    }

    public List<Attachment> attachments() {
        return List.copyOf(attachments);
    }

    public List<UploadRequest> uploadRequests() {
        return List.copyOf(uploadRequests);
    }

    @Override
    public UploadTicket issueUpload(UploadRequest request) {
        uploadRequests.add(request);
        if (uploadRejection != null) {
            throw new UploadNotAllowedException(uploadRejection);
        }
        if (signingFails) {
            throw new MediaStorageUnavailableException(new RuntimeException("no credentials"));
        }
        String fileId = "file-" + uploadRequests.size();
        return new UploadTicket(
                fileId,
                "https://storage.test/incoming/" + fileId,
                Map.of("content-type", request.contentType(), "content-length", String.valueOf(request.sizeBytes())),
                EXPIRES_AT);
    }

    @Override
    public StagedFiles stage(StageRequest request) {
        stageRequests.add(request);
        if (storageDown) {
            throw new MediaStorageUnavailableException(new RuntimeException("down"));
        }
        for (String fileId : request.fileIds()) {
            Reason reason = rejections.get(fileId);
            if (reason != null) {
                return new StagedFiles(
                        request.organizationId(),
                        request.accountId(),
                        request.purpose(),
                        request.fileIds(),
                        java.util.Set.of(),
                        new FileRejection(fileId, reason));
            }
        }
        return new StagedFiles(
                request.organizationId(),
                request.accountId(),
                request.purpose(),
                request.fileIds(),
                new LinkedHashSet<>(request.fileIds()),
                null);
    }

    @Override
    public void attach(StagedFiles staged, String owner) {
        if (staged.findRejection().isPresent()) {
            throw new IllegalArgumentException("rejected files cannot be attached");
        }
        if (attachConflict) {
            throw new AttachmentConflictException(
                    staged.fileIds().isEmpty() ? "none" : staged.fileIds().getFirst());
        }
        attachments.add(new Attachment(staged, owner));
    }

    @Override
    public String downloadUrl(Long organizationId, String fileId) {
        if (signingFails) {
            throw new MediaStorageUnavailableException(new RuntimeException("no credentials"));
        }
        return "url/" + organizationId + "/" + fileId;
    }
}
