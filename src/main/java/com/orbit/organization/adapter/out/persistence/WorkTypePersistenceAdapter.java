package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;

/** 이름 유일 인덱스 위반을 그 자리에서 알 수 있도록 저장마다 flush한다. */
@Repository
class WorkTypePersistenceAdapter implements WorkTypeRepository {

    private final SpringDataWorkTypeRepository repository;

    WorkTypePersistenceAdapter(SpringDataWorkTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkType save(WorkType workType) {
        try {
            return saveAndFlush(workType);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getCause() instanceof ConstraintViolationException violation
                    && WorkTypeJpaEntity.UNDELETED_NAME_INDEX.equalsIgnoreCase(violation.getConstraintName())) {
                throw new DuplicateCategoryNameException(exception);
            }
            throw exception;
        }
    }

    @Override
    public Optional<WorkType> findById(WorkTypeId id) {
        return repository.findById(id.value()).map(WorkTypeJpaEntity::toDomain);
    }

    @Override
    public Optional<WorkType> findUndeletedByName(OrganizationId organizationId, CategoryName name) {
        return repository
                .findByOrganizationIdAndNameAndDeletedFalse(organizationId.value(), name.value())
                .map(WorkTypeJpaEntity::toDomain);
    }

    @Override
    public List<WorkType> listUndeleted(OrganizationId organizationId) {
        return repository.findByOrganizationIdAndDeletedFalseOrderByCreatedAtAscIdAsc(organizationId.value()).stream()
                .map(WorkTypeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean lockIfSelectable(OrganizationId organizationId, WorkTypeId id) {
        return repository.lockUndeleted(organizationId.value(), id.value()).isPresent();
    }

    @Override
    public List<WorkType> listByIds(OrganizationId organizationId, Set<WorkTypeId> ids) {
        return repository
                .findByOrganizationIdAndIdIn(
                        organizationId.value(),
                        ids.stream().map(WorkTypeId::value).toList())
                .stream()
                .map(WorkTypeJpaEntity::toDomain)
                .toList();
    }

    private WorkType saveAndFlush(WorkType workType) {
        if (workType.id().isEmpty()) {
            return repository.saveAndFlush(new WorkTypeJpaEntity(workType)).toDomain();
        }
        WorkTypeJpaEntity entity = repository
                .findById(workType.id().get().value())
                .orElseThrow(() -> new IllegalStateException("work type to update must exist"));
        entity.apply(workType);
        repository.flush();
        return entity.toDomain();
    }
}
