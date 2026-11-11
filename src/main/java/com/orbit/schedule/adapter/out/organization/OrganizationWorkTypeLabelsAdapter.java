package com.orbit.schedule.adapter.out.organization;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.orbit.organization.WorkTypeLabel;
import com.orbit.organization.WorkTypeLookup;
import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * organization의 작업 유형 표시 정보를 schedule의 유형 표시 정보로 옮기는 ACL 어댑터. 삭제된 유형도 삭제 직전 이름·색상과 삭제 표시를 담는 판정과 다른 발주사의
 * 유형을 빼는 판정은 organization 계약이 맡는다. 선택 가능 확인({@link OrganizationWorkTypeAdapter})과 같은 계약을 쓰지만 포트마다 클래스를 나눠, 한 포트만 대체하는
 * 테스트가 다른 포트의 구현까지 지우지 않게 한다.
 */
@Component
class OrganizationWorkTypeLabelsAdapter implements LoadWorkTypeLabelsPort {

    private final WorkTypeLookup workTypeLookup;

    OrganizationWorkTypeLabelsAdapter(WorkTypeLookup workTypeLookup) {
        this.workTypeLookup = workTypeLookup;
    }

    @Override
    public Map<WorkTypeId, TypeLabel> findLabels(OrganizationId organizationId, Set<WorkTypeId> workTypeIds) {
        if (workTypeIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = workTypeIds.stream().map(WorkTypeId::value).collect(Collectors.toSet());
        return workTypeLookup.findLabels(organizationId.value(), ids).stream()
                .collect(Collectors.toUnmodifiableMap(
                        label -> new WorkTypeId(label.workTypeId()), OrganizationWorkTypeLabelsAdapter::toTypeLabel));
    }

    private static TypeLabel toTypeLabel(WorkTypeLabel label) {
        return new TypeLabel(label.workTypeId(), label.name(), label.color(), label.deleted());
    }
}
