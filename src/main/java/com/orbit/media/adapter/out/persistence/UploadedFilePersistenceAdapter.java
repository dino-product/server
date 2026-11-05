package com.orbit.media.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.media.application.port.out.UploadedFileRepository;
import com.orbit.media.domain.UploadedFile;
import com.orbit.media.domain.UploadedFileStatus;

/** 기존 기록은 같은 트랜잭션에서 읽은 managed Entity를 제자리에서 갱신한다. */
@Repository
class UploadedFilePersistenceAdapter implements UploadedFileRepository {

    private final SpringDataUploadedFileRepository repository;

    UploadedFilePersistenceAdapter(SpringDataUploadedFileRepository repository) {
        this.repository = repository;
    }

    @Override
    public UploadedFile save(UploadedFile file) {
        UploadedFileJpaEntity entity = repository.findById(file.id()).orElse(null);
        if (entity == null) {
            return repository.save(UploadedFileJpaEntity.from(file)).toDomain();
        }
        entity.update(file);
        return entity.toDomain();
    }

    @Override
    public List<UploadedFile> listByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllById(ids).stream()
                .map(UploadedFileJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UploadedFile> lockForAttachment(
            Collection<UUID> ids, long organizationId, String purpose, String owner) {
        List<UploadedFileJpaEntity> locked = ids.isEmpty()
                ? repository.lockByOwner(organizationId, purpose, owner, UploadedFileStatus.ATTACHED)
                : repository.lockByIdsOrOwner(ids, organizationId, purpose, owner, UploadedFileStatus.ATTACHED);
        return locked.stream().map(UploadedFileJpaEntity::toDomain).toList();
    }
}
