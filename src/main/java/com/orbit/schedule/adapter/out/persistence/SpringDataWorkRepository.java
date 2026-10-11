package com.orbit.schedule.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.orbit.schedule.domain.WorkStatus;

interface SpringDataWorkRepository extends JpaRepository<WorkJpaEntity, Long> {

    Optional<WorkJpaEntity> findByIdAndCompanyId(Long id, Long companyId);

    List<WorkJpaEntity> findByCompanyIdAndTechnicianIdAndStatusIn(
            Long companyId, Long technicianId, Collection<WorkStatus> statuses);
}
