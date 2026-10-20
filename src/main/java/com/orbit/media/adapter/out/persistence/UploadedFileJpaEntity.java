package com.orbit.media.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.orbit.media.StageRequest;
import com.orbit.media.UploadSpec;
import com.orbit.media.domain.UploadedFile;
import com.orbit.media.domain.UploadedFileStatus;
import com.orbit.shared.persistence.BaseTimeEntity;

/**
 * 업로드 파일 기록. 발주사·업로더 계정은 다른 모듈의 식별자라 ID로만 둔다. {@code object_key}는 지금 상태의 객체 위치(운영 조회용)이며 Domain은 식별자·발주사·상태로
 * 다시 계산한다. 연결 대상({@code owner_ref})은 호출 모듈이 정한 문자열이다. 식별자를 Domain이 정하므로 새 행 여부는 {@code version}으로 판단한다. ERD에 없는 테이블이라
 * ERD에 추가한다.
 */
@Entity
@Table(
        name = "uploaded_files",
        indexes = @Index(name = "ix_uploaded_files_owner", columnList = "organization_id, purpose, owner_ref"))
class UploadedFileJpaEntity extends BaseTimeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "uploader_account_id", nullable = false, updatable = false)
    private Long uploaderAccountId;

    @Column(name = "purpose", nullable = false, updatable = false, length = UploadSpec.MAX_PURPOSE_LENGTH)
    private String purpose;

    @Column(name = "object_key", nullable = false, length = 200)
    private String objectKey;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UploadedFileStatus status;

    @Column(name = "owner_ref", length = StageRequest.MAX_OWNER_LENGTH)
    private String ownerRef;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Column(name = "retain_until")
    private Instant retainUntil;

    protected UploadedFileJpaEntity() {}

    private UploadedFileJpaEntity(UploadedFile file) {
        this.id = file.id();
        this.organizationId = file.organizationId();
        this.uploaderAccountId = file.uploaderAccountId();
        this.purpose = file.purpose();
        this.contentType = file.contentType();
        this.sizeBytes = file.sizeBytes();
        update(file);
    }

    static UploadedFileJpaEntity from(UploadedFile file) {
        return new UploadedFileJpaEntity(file);
    }

    /** 발급 때 정한 값은 바뀌지 않으므로 상태·연결 기록만 옮긴다. */
    void update(UploadedFile file) {
        this.objectKey = file.objectKey();
        this.status = file.status();
        this.ownerRef = file.owner().orElse(null);
        this.attachedAt = file.attachedAt().orElse(null);
        this.retainUntil = file.retainUntil().orElse(null);
    }

    UUID id() {
        return id;
    }

    UploadedFile toDomain() {
        return UploadedFile.reconstitute(
                id,
                organizationId,
                uploaderAccountId,
                purpose,
                contentType,
                sizeBytes,
                status,
                ownerRef,
                attachedAt,
                retainUntil);
    }
}
