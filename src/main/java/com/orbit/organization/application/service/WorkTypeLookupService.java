package com.orbit.organization.application.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.organization.WorkTypeLabel;
import com.orbit.organization.WorkTypeLookup;
import com.orbit.organization.application.port.out.WorkTypeRepository;
import com.orbit.organization.domain.OrganizationId;
import com.orbit.organization.domain.WorkType;
import com.orbit.organization.domain.WorkTypeId;

/** 선택 가능 확인은 유형 행을 잠그므로 호출 트랜잭션에 참여하는 읽기·쓰기 트랜잭션으로 둔다. */
@Service
public class WorkTypeLookupService implements WorkTypeLookup {

    private final WorkTypeRepository workTypeRepository;

    public WorkTypeLookupService(WorkTypeRepository workTypeRepository) {
        this.workTypeRepository = workTypeRepository;
    }

    @Override
    @Transactional
    public boolean isSelectable(Long organizationId, Long workTypeId) {
        return workTypeRepository.lockIfSelectable(new OrganizationId(organizationId), new WorkTypeId(workTypeId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkTypeLabel> findLabels(Long organizationId, Set<Long> workTypeIds) {
        OrganizationId organization = new OrganizationId(organizationId);
        if (workTypeIds == null) {
            throw new IllegalArgumentException("workTypeIds must not be null");
        }
        Set<WorkTypeId> ids = workTypeIds.stream().map(WorkTypeId::new).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return workTypeRepository.listByIds(organization, ids).stream()
                .map(WorkTypeLookupService::toLabel)
                .toList();
    }

    private static WorkTypeLabel toLabel(WorkType workType) {
        return new WorkTypeLabel(
                workType.id()
                        .orElseThrow(() -> new IllegalArgumentException("workType must have an id"))
                        .value(),
                workType.name().value(),
                workType.color().value(),
                workType.isDeleted());
    }
}
