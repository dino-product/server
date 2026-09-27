package com.orbit.organization.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Repository;

import com.orbit.organization.application.port.out.PersonnelTypeNameConflictException;
import com.orbit.organization.application.port.out.TechnicianTypeRepository;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.PersonnelTypeName;
import com.orbit.organization.domain.TechnicianType;
import com.orbit.organization.domain.TechnicianTypeId;

@Repository
class TechnicianTypePersistenceAdapter implements TechnicianTypeRepository {
    private final EntityManager entityManager;
    private final SpringDataTechnicianTypeRepository technicianTypes;

    TechnicianTypePersistenceAdapter(EntityManager entityManager, SpringDataTechnicianTypeRepository technicianTypes) {
        this.entityManager = entityManager;
        this.technicianTypes = technicianTypes;
    }

    @Override
    public TechnicianTypeId nextId() {
        var value = entityManager
                .createNativeQuery("select nextval('technician_type_id_seq')")
                .getSingleResult();
        return new TechnicianTypeId(((Number) value).longValue());
    }

    @Override
    public void save(TechnicianType type) {
        try {
            var existing = technicianTypes.findById(type.id().value());
            if (existing.isPresent()) {
                existing.orElseThrow().updateFrom(type);
            } else {
                entityManager.persist(TechnicianTypeJpaEntity.from(type));
            }
            entityManager.flush();
        } catch (RuntimeException failure) {
            if (isNameConflict(failure)) {
                throw new PersonnelTypeNameConflictException(failure);
            }
            throw failure;
        }
    }

    @Override
    public Optional<TechnicianType> findByIdForUpdate(TechnicianTypeId id) {
        return technicianTypes.findByIdForUpdate(id.value()).map(TechnicianTypeJpaEntity::toDomain);
    }

    @Override
    public List<TechnicianType> findAllByOrganization(OrganizationId organizationId) {
        return technicianTypes.findAllByOrganizationIdOrderByIdAsc(organizationId.value()).stream()
                .map(TechnicianTypeJpaEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOrganizationAndName(
            OrganizationId organizationId, PersonnelTypeName name, TechnicianTypeId excludeId) {
        if (excludeId == null) {
            return technicianTypes.existsByOrganizationIdAndName(organizationId.value(), name.value());
        }
        return technicianTypes.existsByOrganizationIdAndNameAndIdNot(
                organizationId.value(), name.value(), excludeId.value());
    }

    @Override
    public void delete(TechnicianTypeId id) {
        technicianTypes.findById(id.value()).ifPresent(entityManager::remove);
        entityManager.flush();
    }

    private boolean isNameConflict(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uq_technician_type_organization_name".equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
