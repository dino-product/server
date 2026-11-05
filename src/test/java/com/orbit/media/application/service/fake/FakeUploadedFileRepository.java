package com.orbit.media.application.service.fake;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.orbit.media.application.port.out.UploadedFileRepository;
import com.orbit.media.domain.UploadedFile;

/** 메모리 저장소. 저장한 사본과 분리된 복원 사본을 돌려줘 실제 저장소처럼 save하지 않은 변경은 남지 않는다. */
public class FakeUploadedFileRepository implements UploadedFileRepository {

    private final Map<UUID, UploadedFile> files = new LinkedHashMap<>();
    private int lockCalls;

    @Override
    public UploadedFile save(UploadedFile file) {
        files.put(file.id(), copy(file));
        return copy(file);
    }

    @Override
    public List<UploadedFile> listByIds(Collection<UUID> ids) {
        return ids.stream()
                .map(files::get)
                .filter(java.util.Objects::nonNull)
                .map(FakeUploadedFileRepository::copy)
                .toList();
    }

    @Override
    public List<UploadedFile> lockForAttachment(
            Collection<UUID> ids, long organizationId, String purpose, String owner) {
        lockCalls++;
        return files.values().stream()
                .filter(file -> ids.contains(file.id()) || file.isAttachedTo(organizationId, purpose, owner))
                .sorted(Comparator.comparing(UploadedFile::id))
                .map(FakeUploadedFileRepository::copy)
                .toList();
    }

    public Optional<UploadedFile> find(UUID id) {
        return Optional.ofNullable(files.get(id)).map(FakeUploadedFileRepository::copy);
    }

    public List<UploadedFile> all() {
        return files.values().stream().map(FakeUploadedFileRepository::copy).toList();
    }

    public int lockCalls() {
        return lockCalls;
    }

    private static UploadedFile copy(UploadedFile file) {
        return UploadedFile.reconstitute(
                file.id(),
                file.organizationId(),
                file.uploaderAccountId(),
                file.purpose(),
                file.contentType(),
                file.sizeBytes(),
                file.status(),
                file.owner().orElse(null),
                file.attachedAt().orElse(null),
                file.retainUntil().orElse(null));
    }
}
