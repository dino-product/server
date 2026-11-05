package com.orbit.media.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import com.orbit.media.application.port.out.ObjectStoragePort;
import com.orbit.media.application.port.out.ObjectStorageUnavailableException;
import com.orbit.media.application.port.out.PresignedUpload;
import com.orbit.media.application.port.out.StoredObject;
import com.orbit.media.application.port.out.UploadedFileRepository;
import com.orbit.media.domain.ImageFormat;
import com.orbit.media.domain.ObjectKeys;
import com.orbit.media.domain.UploadedFile;
import com.orbit.media.domain.UploadedFilesExpired;

/**
 * {@link MediaFiles} 구현. 저장소 호출(확인·복사)은 {@link #stage}에서 트랜잭션 없이 끝내고, DB 확정({@link #attach})은 호출자 트랜잭션에서 한다. 저장소에
 * 복사한 뒤 업무가 실패하면 파일은 PENDING으로 남고 같은 요청의 재시도가 다시 복사·연결한다. 만료 표시는 커밋 뒤 {@link UploadedFilesExpired}를 받아 남긴다.
 */
@Service
public class MediaFilesService implements MediaFiles {

    private final UploadedFileRepository repository;
    private final ObjectStoragePort storage;
    private final MediaFileProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MediaFilesService(
            UploadedFileRepository repository,
            ObjectStoragePort storage,
            MediaFileProperties properties,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 서명은 저장소를 부르지 않으므로 호출자 트랜잭션 안에서 해도 된다. 서명하지 못하면 기록을 남기지 않는다. */
    @Override
    @Transactional
    public UploadTicket issueUpload(UploadRequest request) {
        if (!properties.allows(request.contentType())) {
            throw new UploadNotAllowedException(UploadNotAllowedException.Reason.CONTENT_TYPE_NOT_ALLOWED);
        }
        if (request.sizeBytes() < 1 || request.sizeBytes() > request.spec().maxBytes()) {
            throw new UploadNotAllowedException(UploadNotAllowedException.Reason.SIZE_NOT_ALLOWED);
        }
        UploadedFile file = UploadedFile.issue(
                UUID.randomUUID(),
                request.organizationId(),
                request.uploaderAccountId(),
                request.spec().purpose(),
                request.contentType(),
                request.sizeBytes());
        PresignedUpload upload;
        try {
            upload = storage.presignUpload(file.incomingKey(), file.contentType(), file.sizeBytes());
        } catch (ObjectStorageUnavailableException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
        repository.save(file);
        return new UploadTicket(file.id().toString(), upload.url(), upload.headers(), upload.expiresAt());
    }

    /** 트랜잭션 없이 실행한다(저장소 지연이 DB 연결을 붙잡지 않게). 기록 조회는 저장소의 짧은 읽기 트랜잭션으로 한다. */
    @Override
    public StagedFiles stage(StageRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("stage must be called outside a transaction");
        }
        List<String> fileIds = request.fileIds();
        if (fileIds.isEmpty()) {
            return StagedFiles.empty(request.organizationId(), request.accountId(), request.purpose());
        }
        Optional<FileRejection> invalid = findInvalid(fileIds);
        if (invalid.isPresent()) {
            return rejected(request, invalid.get());
        }
        Map<String, UUID> ids = new LinkedHashMap<>();
        fileIds.forEach(fileId -> ids.put(fileId, UUID.fromString(fileId)));
        if (request.organizationId() == null || request.accountId() == null) {
            return rejected(request, new FileRejection(fileIds.getFirst(), Reason.NOT_UPLOADED));
        }
        long organizationId = request.organizationId();
        Map<UUID, UploadedFile> files = repository.listByIds(ids.values()).stream()
                .collect(Collectors.toMap(UploadedFile::id, Function.identity()));

        // 저장소를 부르기 전에 기록만으로 거를 수 있는 파일을 먼저 거른다.
        List<UploadedFile> pending = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : ids.entrySet()) {
            UploadedFile file = files.get(entry.getValue());
            if (file != null
                    && request.owner() != null
                    && file.isAttachedTo(organizationId, request.purpose(), request.owner())) {
                continue;
            }
            if (file == null || !file.isPendingFor(organizationId, request.accountId(), request.purpose())) {
                return rejected(request, new FileRejection(entry.getKey(), Reason.NOT_UPLOADED));
            }
            pending.add(file);
        }

        Set<String> copied = new LinkedHashSet<>();
        try {
            for (UploadedFile file : pending) {
                Optional<StoredObject> stored = storage.find(file.incomingKey());
                Optional<Reason> reason =
                        stored.isEmpty() ? Optional.of(Reason.NOT_UPLOADED) : verifyUploaded(file, stored.get());
                if (reason.isPresent()) {
                    return rejected(request, new FileRejection(file.id().toString(), reason.get()));
                }
                // 확인한 내용(ETag)일 때만 복사한다. 그 사이 다시 올리거나 지워졌으면 업로드를 마치지 않은 것으로 본다.
                // 내려받기 응답의 형식은 업로더가 붙인 값이 아니라 발급 때 기록하고 확인한 형식으로 둔다.
                if (!storage.copy(
                        file.incomingKey(), file.mediaKey(), stored.get().eTag(), file.contentType())) {
                    return rejected(request, new FileRejection(file.id().toString(), Reason.NOT_UPLOADED));
                }
                copied.add(file.id().toString());
            }
        } catch (ObjectStorageUnavailableException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
        return new StagedFiles(organizationId, request.accountId(), request.purpose(), fileIds, copied, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attach(StagedFiles staged, String owner) {
        if (staged.findRejection().isPresent()) {
            throw new IllegalArgumentException("rejected files cannot be attached");
        }
        StageRequest.requireOwner(owner);
        if (staged.organizationId() == null || staged.accountId() == null) {
            throw new IllegalArgumentException("staged files must have the requester");
        }
        long organizationId = staged.organizationId();
        List<UUID> ids = staged.fileIds().stream().map(UUID::fromString).toList();
        Map<UUID, UploadedFile> locked = new HashMap<>();
        repository
                .lockForAttachment(ids, organizationId, staged.purpose(), owner)
                .forEach(file -> locked.put(file.id(), file));

        Instant now = clock.instant();
        for (UUID id : ids) {
            UploadedFile file = locked.get(id);
            if (file != null && file.isAttachedTo(organizationId, staged.purpose(), owner)) {
                continue;
            }
            if (file == null
                    || !staged.copied().contains(id.toString())
                    || !file.isPendingFor(organizationId, staged.accountId(), staged.purpose())) {
                throw new AttachmentConflictException(id.toString());
            }
            file.attach(owner, now);
            repository.save(file);
        }

        Set<UUID> kept = new HashSet<>(ids);
        List<String> expiredKeys = new ArrayList<>();
        for (UploadedFile file : locked.values()) {
            if (!kept.contains(file.id()) && file.isAttachedTo(organizationId, staged.purpose(), owner)) {
                file.expire();
                repository.save(file);
                expiredKeys.add(file.mediaKey());
            }
        }
        if (!expiredKeys.isEmpty()) {
            eventPublisher.publishEvent(new UploadedFilesExpired(expiredKeys));
        }
    }

    @Override
    public String downloadUrl(Long organizationId, String fileId) {
        try {
            return storage.presignDownload(ObjectKeys.media(organizationId, fileId));
        } catch (ObjectStorageUnavailableException exception) {
            throw new MediaStorageUnavailableException(exception);
        }
    }

    /**
     * 올라온 객체가 발급 때 밝힌 형식·크기 그대로이며 앞부분이 그 형식의 이미지 시그니처인지. 앞부분은 메타데이터를 확인한 내용(ETag)에서만 읽으므로, 그 사이 다시
     * 올렸거나 지워졌으면 업로드를 마치지 않은 것으로 본다.
     */
    private Optional<Reason> verifyUploaded(UploadedFile file, StoredObject stored) {
        if (stored.sizeBytes() > file.sizeBytes()) {
            return Optional.of(Reason.TOO_LARGE);
        }
        if (stored.sizeBytes() != file.sizeBytes()) {
            return Optional.of(Reason.NOT_UPLOADED);
        }
        if (stored.contentType() == null
                || !ImageFormat.normalize(stored.contentType()).equals(file.contentType())) {
            return Optional.of(Reason.CONTENT_TYPE_MISMATCH);
        }
        Optional<byte[]> head = storage.readFirstBytes(file.incomingKey(), ImageFormat.SIGNATURE_LENGTH, stored.eTag());
        if (head.isEmpty()) {
            return Optional.of(Reason.NOT_UPLOADED);
        }
        Optional<ImageFormat> declared = ImageFormat.ofContentType(file.contentType());
        Optional<ImageFormat> detected = ImageFormat.detect(head.get());
        if (declared.isEmpty() || !declared.equals(detected)) {
            return Optional.of(Reason.CONTENT_TYPE_MISMATCH);
        }
        return Optional.empty();
    }

    /** 중복과 UUID가 아닌 식별자. 기록·저장소를 보기 전에 거른다. */
    private static Optional<FileRejection> findInvalid(List<String> fileIds) {
        Set<String> seen = new HashSet<>();
        for (String fileId : fileIds) {
            if (!isUuid(fileId)) {
                return Optional.of(new FileRejection(fileId, Reason.NOT_UPLOADED));
            }
            if (!seen.add(UUID.fromString(fileId).toString())) {
                return Optional.of(new FileRejection(fileId, Reason.DUPLICATED));
            }
        }
        return Optional.empty();
    }

    /** 발급한 식별자 문자열(소문자 정규형)만 받는다. */
    private static boolean isUuid(String fileId) {
        if (fileId == null) {
            return false;
        }
        try {
            return UUID.fromString(fileId).toString().equals(fileId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static StagedFiles rejected(StageRequest request, FileRejection rejection) {
        return new StagedFiles(
                request.organizationId(),
                request.accountId(),
                request.purpose(),
                request.fileIds(),
                Set.of(),
                rejection);
    }
}
