package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.DuplicateCategoryNameException;
import com.orbit.organization.application.port.out.MemberCategoryRepository;
import com.orbit.organization.domain.CategoryName;
import com.orbit.organization.domain.MemberCategory;
import com.orbit.organization.domain.MemberCategoryId;
import com.orbit.organization.domain.MemberCategoryKind;
import com.orbit.organization.domain.OrganizationId;

/** 종류에 따라 직원 유형 테이블이나 기사 유형 테이블을 쓴다. 이름 유일 인덱스 위반을 그 자리에서 알 수 있도록 저장마다 flush한다. */
@Repository
class MemberCategoryPersistenceAdapter implements MemberCategoryRepository {

    private final SpringDataStaffCategoryRepository staffRepository;
    private final SpringDataTechnicianCategoryRepository technicianRepository;

    MemberCategoryPersistenceAdapter(
            SpringDataStaffCategoryRepository staffRepository,
            SpringDataTechnicianCategoryRepository technicianRepository) {
        this.staffRepository = staffRepository;
        this.technicianRepository = technicianRepository;
    }

    @Override
    public MemberCategory save(MemberCategory category) {
        try {
            return saveAndFlush(category);
        } catch (DataIntegrityViolationException exception) {
            if (violatesUndeletedNameIndex(exception)) {
                throw new DuplicateCategoryNameException(exception);
            }
            throw exception;
        }
    }

    @Override
    public Optional<MemberCategory> findById(MemberCategoryKind kind, MemberCategoryId id) {
        return repository(kind).findById(id.value()).map(MemberCategoryJpaEntity::toDomain);
    }

    @Override
    public Optional<MemberCategory> findUndeletedByName(
            OrganizationId organizationId, MemberCategoryKind kind, CategoryName name) {
        return repository(kind)
                .findByOrganizationIdAndNameAndDeletedFalse(organizationId.value(), name.value())
                .map(MemberCategoryJpaEntity::toDomain);
    }

    @Override
    public List<MemberCategory> listUndeleted(OrganizationId organizationId, MemberCategoryKind kind) {
        return repository(kind)
                .findByOrganizationIdAndDeletedFalseOrderByCreatedAtAscIdAsc(organizationId.value())
                .stream()
                .map(MemberCategoryJpaEntity::toDomain)
                .toList();
    }

    private MemberCategory saveAndFlush(MemberCategory category) {
        if (category.id().isEmpty()) {
            return switch (category.kind()) {
                case STAFF ->
                    staffRepository
                            .saveAndFlush(new StaffCategoryJpaEntity(category))
                            .toDomain();
                case TECHNICIAN ->
                    technicianRepository
                            .saveAndFlush(new TechnicianCategoryJpaEntity(category))
                            .toDomain();
            };
        }
        SpringDataMemberCategoryRepository<? extends MemberCategoryJpaEntity> repository = repository(category.kind());
        MemberCategoryJpaEntity entity = repository
                .findById(category.id().get().value())
                .orElseThrow(() -> new IllegalStateException("category to update must exist"));
        entity.apply(category);
        repository.flush();
        return entity.toDomain();
    }

    private static boolean violatesUndeletedNameIndex(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException violation
                && (StaffCategoryJpaEntity.UNDELETED_NAME_INDEX.equalsIgnoreCase(violation.getConstraintName())
                        || TechnicianCategoryJpaEntity.UNDELETED_NAME_INDEX.equalsIgnoreCase(
                                violation.getConstraintName()));
    }

    private SpringDataMemberCategoryRepository<? extends MemberCategoryJpaEntity> repository(MemberCategoryKind kind) {
        return switch (kind) {
            case STAFF -> staffRepository;
            case TECHNICIAN -> technicianRepository;
        };
    }
}
