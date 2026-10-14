package com.orbit.schedule.application.service.fake;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * 서비스 단위 테스트용 WorkTypePort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 등록한 조직·유형 조합만 선택할 수 있는 유형으로 보고, 확인 요청 횟수를 기록해
 * 확인 전에 끝나야 하는 요청이 포트를 부르지 않았는지 보게 한다.
 */
public class FakeWorkTypePort implements WorkTypePort {

    private final Set<WorkType> selectableTypes = new HashSet<>();
    private int checks;

    public void givenSelectable(OrganizationId organizationId, WorkTypeId workTypeId) {
        selectableTypes.add(new WorkType(organizationId, workTypeId));
    }

    public void givenNotSelectable(OrganizationId organizationId, WorkTypeId workTypeId) {
        selectableTypes.remove(new WorkType(organizationId, workTypeId));
    }

    public int checks() {
        return checks;
    }

    @Override
    public boolean isSelectable(OrganizationId organizationId, WorkTypeId workTypeId) {
        // 실제 포트도 식별자를 요구하므로, 서비스가 유형 없는 요청에도 부르면 여기서 다른 메시지로 드러난다.
        Objects.requireNonNull(organizationId, "fake port received null organizationId");
        Objects.requireNonNull(workTypeId, "fake port received null workTypeId");
        checks++;
        return selectableTypes.contains(new WorkType(organizationId, workTypeId));
    }

    private record WorkType(OrganizationId organizationId, WorkTypeId workTypeId) {}
}
