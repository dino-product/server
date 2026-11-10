package com.orbit.schedule.application.port.out;

import java.util.Set;

import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;

/** 조직 소속이 등록한 작업. 조직 범위이고 인자는 null이 아니며 상태 집합은 비어 있지 않다. */
public interface RegisteredWorksPort {

    /** 그 조직에서 이 소속이 등록한 작업 중 지정한 상태인 작업이 하나라도 있는지. 잠금을 잡지 않고 커밋된 상태를 읽는다. */
    boolean existsRegisteredBy(OrganizationId organizationId, MembershipId registrarId, Set<WorkStatus> statuses);
}
