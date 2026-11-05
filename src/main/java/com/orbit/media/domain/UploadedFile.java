package com.orbit.media.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 업로드 주소를 발급한 파일 한 건. 발주사·업로더·용도·형식·크기는 발급 때 정해져 바뀌지 않고, 상태만 업무 메서드로 바뀐다. 연결 대상({@code owner})은 호출 모듈이
 * 정한 식별 문자열이며 이 모듈은 뜻을 해석하지 않는다.
 */
public final class UploadedFile {

    private final UUID id;
    private final long organizationId;
    private final long uploaderAccountId;
    private final String purpose;
    private final String contentType;
    private final long sizeBytes;
    private UploadedFileStatus status;
    private String owner;
    private Instant attachedAt;
    private final Instant retainUntil;

    private UploadedFile(
            UUID id,
            long organizationId,
            long uploaderAccountId,
            String purpose,
            String contentType,
            long sizeBytes,
            UploadedFileStatus status,
            String owner,
            Instant attachedAt,
            Instant retainUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.organizationId = organizationId;
        this.uploaderAccountId = uploaderAccountId;
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.sizeBytes = sizeBytes;
        this.status = Objects.requireNonNull(status, "status");
        this.owner = owner;
        this.attachedAt = attachedAt;
        this.retainUntil = retainUntil;
    }

    /** 업로드 주소를 발급한 새 파일. */
    public static UploadedFile issue(
            UUID id, long organizationId, long uploaderAccountId, String purpose, String contentType, long sizeBytes) {
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        return new UploadedFile(
                id,
                organizationId,
                uploaderAccountId,
                purpose,
                ImageFormat.normalize(contentType),
                sizeBytes,
                UploadedFileStatus.PENDING,
                null,
                null,
                null);
    }

    /** 저장값 복원. 연결됐던 파일(ATTACHED·EXPIRED)은 대상과 연결 시각이 있어야 하고 PENDING은 없어야 한다. */
    public static UploadedFile reconstitute(
            UUID id,
            long organizationId,
            long uploaderAccountId,
            String purpose,
            String contentType,
            long sizeBytes,
            UploadedFileStatus status,
            String owner,
            Instant attachedAt,
            Instant retainUntil) {
        boolean attachedOnce = status != UploadedFileStatus.PENDING;
        if (attachedOnce != (owner != null) || attachedOnce != (attachedAt != null)) {
            throw new IllegalArgumentException("owner and attachedAt must exist exactly when the file was attached");
        }
        return new UploadedFile(
                id,
                organizationId,
                uploaderAccountId,
                purpose,
                contentType,
                sizeBytes,
                status,
                owner,
                attachedAt,
                retainUntil);
    }

    /** 이 요청자(발주사·계정)가 이 용도로 발급받아 아직 연결하지 않은 파일인가. */
    public boolean isPendingFor(long organizationId, long accountId, String purpose) {
        return status == UploadedFileStatus.PENDING
                && this.organizationId == organizationId
                && uploaderAccountId == accountId
                && this.purpose.equals(purpose);
    }

    /** 이 발주사의 이 대상에 이 용도로 연결돼 있는가. */
    public boolean isAttachedTo(long organizationId, String purpose, String owner) {
        return status == UploadedFileStatus.ATTACHED
                && this.organizationId == organizationId
                && this.purpose.equals(purpose)
                && Objects.equals(this.owner, owner);
    }

    /** PENDING 파일을 대상에 연결한다. */
    public void attach(String owner, Instant now) {
        if (status != UploadedFileStatus.PENDING) {
            throw new IllegalStateException("only pending files can be attached: " + status);
        }
        this.owner = Objects.requireNonNull(owner, "owner");
        this.attachedAt = Objects.requireNonNull(now, "now");
        this.status = UploadedFileStatus.ATTACHED;
    }

    /** 연결된 파일을 대상에서 빼 만료로 바꾼다. 대상·연결 시각은 기록으로 남긴다. */
    public void expire() {
        if (status != UploadedFileStatus.ATTACHED) {
            throw new IllegalStateException("only attached files can expire: " + status);
        }
        this.status = UploadedFileStatus.EXPIRED;
    }

    /** 지금 상태의 객체 키. 발급·업로드 대기는 {@code incoming/}, 연결된 뒤에는 {@code media/}. */
    public String objectKey() {
        return status == UploadedFileStatus.PENDING ? incomingKey() : mediaKey();
    }

    public String incomingKey() {
        return ObjectKeys.incoming(id);
    }

    public String mediaKey() {
        return ObjectKeys.media(organizationId, id.toString());
    }

    public UUID id() {
        return id;
    }

    public long organizationId() {
        return organizationId;
    }

    public long uploaderAccountId() {
        return uploaderAccountId;
    }

    public String purpose() {
        return purpose;
    }

    public String contentType() {
        return contentType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public UploadedFileStatus status() {
        return status;
    }

    public Optional<String> owner() {
        return Optional.ofNullable(owner);
    }

    public Optional<Instant> attachedAt() {
        return Optional.ofNullable(attachedAt);
    }

    public Optional<Instant> retainUntil() {
        return Optional.ofNullable(retainUntil);
    }
}
