package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.application.port.out.LoadWorkTypeLabelsPort;
import com.orbit.schedule.application.port.out.TypeLabel;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/** 서비스 단위 테스트용 LoadWorkTypeLabelsPort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 테스트가 넣은 조직별 작업유형 표시 정보만 돌려준다. */
public class FakeWorkTypeLabelsPort implements LoadWorkTypeLabelsPort {

    private final Map<OrganizationId, Map<WorkTypeId, TypeLabel>> labels = new HashMap<>();

    private final List<Set<WorkTypeId>> lookups = new ArrayList<>();

    /** 표시 정보를 찾은 요청의 식별자 집합. */
    public List<Set<WorkTypeId>> lookups() {
        return List.copyOf(lookups);
    }

    public void given(OrganizationId organizationId, TypeLabel label) {
        labels.computeIfAbsent(organizationId, ignored -> new HashMap<>()).put(new WorkTypeId(label.id()), label);
    }

    @Override
    public Map<WorkTypeId, TypeLabel> findLabels(OrganizationId organizationId, Set<WorkTypeId> workTypeIds) {
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        lookups.add(Set.copyOf(workTypeIds));
        Map<WorkTypeId, TypeLabel> ofOrganization = labels.getOrDefault(organizationId, Map.of());
        Map<WorkTypeId, TypeLabel> found = new HashMap<>();
        for (WorkTypeId workTypeId : workTypeIds) {
            TypeLabel label = ofOrganization.get(workTypeId);
            if (label != null) {
                found.put(workTypeId, label);
            }
        }
        return Map.copyOf(found);
    }
}
