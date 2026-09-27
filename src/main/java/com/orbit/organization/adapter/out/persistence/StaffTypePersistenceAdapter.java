package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.PersonnelTypeInUseException;
import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.StaffTypeRepository;
import com.orbit.organization.application.port.out.StaffTypeUsagePort;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.StaffType;
import com.orbit.organization.domain.StaffTypeId;

@Repository
class StaffTypePersistenceAdapter implements StaffTypeRepository, StaffTypeUsagePort {
    private final EntityManager entityManager;
    private final SpringDataStaffTypeRepository staffTypes;
    private final SpringDataMembershipRepository memberships;

    StaffTypePersistenceAdapter(
            EntityManager entityManager,
            SpringDataStaffTypeRepository staffTypes,
            SpringDataMembershipRepository memberships) {
        this.entityManager = entityManager;
        this.staffTypes = staffTypes;
        this.memberships = memberships;
    }

    @Override
    public StaffTypeId nextId() {
        var value = entityManager
                .createNativeQuery("select nextval('staff_type_id_seq')")
                .getSingleResult();
        return new StaffTypeId(((Number) value).longValue());
    }

    @Override
    public void save(StaffType type) {
        try {
            var existing = staffTypes.findById(type.id().value());
            if (existing.isPresent()) {
                existing.orElseThrow().updateFrom(type);
            } else {
                entityManager.persist(StaffTypeJpaEntity.from(type));
            }
            entityManager.flush();
        } catch (RuntimeException failure) {
            if (isConstraint(failure, "23505", "uq_staff_type_organization_name")) {
                throw new PersonnelTypeNameConflictException(failure);
            }
            throw failure;
        }
    }

    @Override
    public Optional<StaffType> findByIdForUpdate(StaffTypeId id) {
        return staffTypes.findByIdForUpdate(id.value()).map(StaffTypeJpaEntity::toDomain);
    }

    @Override
    public List<StaffType> findAllByOrganization(OrganizationId organizationId) {
        return staffTypes.findAllByOrganizationIdOrderByIdAsc(organizationId.value()).stream()
                .map(StaffTypeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOrganizationAndName(
            OrganizationId organizationId, PersonnelTypeName name, StaffTypeId excludeId) {
        if (excludeId == null) {
            return staffTypes.existsByOrganizationIdAndName(organizationId.value(), name.value());
        }
        return staffTypes.existsByOrganizationIdAndNameAndIdNot(
                organizationId.value(), name.value(), excludeId.value());
    }

    @Override
    public boolean isAssigned(StaffTypeId id) {
        return memberships.existsByStaffTypeId(id.value());
    }

    @Override
    public void delete(StaffTypeId id) {
        try {
            staffTypes.findById(id.value()).ifPresent(entityManager::remove);
            entityManager.flush();
        } catch (RuntimeException failure) {
            if (isConstraint(failure, "23503", "fk_membership_staff_type")) {
                throw new PersonnelTypeInUseException(failure);
            }
            throw failure;
        }
    }

    private boolean isConstraint(Throwable failure, String state, String name) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && state.equals(violation.getSQLState())
                    && name.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
