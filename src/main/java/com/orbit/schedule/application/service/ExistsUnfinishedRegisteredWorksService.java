package com.orbit.schedule.application.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.ExistsUnfinishedRegisteredWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.UnfinishedRegisteredWorksQuery;
import com.orbit.schedule.application.port.out.RegisteredWorksPort;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 직원을 기사로 바꾸기 전 그 직원이 등록한 작업 중 끝나지 않은 작업(종료 상태가 아닌 작업: 대기함·배정됨·작업전·작업중)이 있는지 확인한다. 완료·취소 작업은 보지
 * 않는다. 기준은 {@link WorkStatus#isTerminal}이다.
 */
@Service
public class ExistsUnfinishedRegisteredWorksService implements ExistsUnfinishedRegisteredWorksUseCase {

    private static final Set<WorkStatus> UNFINISHED = Arrays.stream(WorkStatus.values())
            .filter(status -> !status.isTerminal())
            .collect(Collectors.toUnmodifiableSet());

    private final RegisteredWorksPort registeredWorksPort;

    public ExistsUnfinishedRegisteredWorksService(RegisteredWorksPort registeredWorksPort) {
        this.registeredWorksPort = registeredWorksPort;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UnfinishedRegisteredWorksQuery query) {
        return registeredWorksPort.existsRegisteredBy(
                new OrganizationId(query.organizationId()), new MembershipId(query.membershipId()), UNFINISHED);
    }
}
