package com.orbit.schedule.adapter.out.organization;

import org.springframework.stereotype.Component;

import com.orbit.organization.WorkTypeLookup;
import com.orbit.schedule.application.port.out.WorkTypePort;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkTypeId;

/**
 * organization의 작업 유형 선택 가능 확인을 schedule의 작업 유형 확인으로 옮기는 ACL 어댑터. 다른 발주사·삭제된·없는 유형을 구분하지 않는 판정과 참이면 유형 행을
 * 호출 트랜잭션이 끝날 때까지 공유 잠금으로 잡는 보장은 organization 계약이 맡는다. 그래서 작업 저장과 같은 읽기·쓰기 트랜잭션에서, 작업 행을 바꾸기 전에 불러야
 * 한다(호출 위치는 {@code OrganizationReferences}).
 */
@Component
class OrganizationWorkTypeAdapter implements WorkTypePort {

    private final WorkTypeLookup workTypeLookup;

    OrganizationWorkTypeAdapter(WorkTypeLookup workTypeLookup) {
        this.workTypeLookup = workTypeLookup;
    }

    @Override
    public boolean isSelectable(OrganizationId organizationId, WorkTypeId workTypeId) {
        return workTypeLookup.isSelectable(organizationId.value(), workTypeId.value());
    }
}
