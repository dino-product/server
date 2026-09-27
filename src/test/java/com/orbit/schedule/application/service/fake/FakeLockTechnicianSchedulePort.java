package com.orbit.schedule.application.service.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.domain.MembershipId;
import com.orbit.schedule.domain.OrganizationId;

/**
 * 서비스 단위 테스트용 LockTechnicianSchedulePort. 스캔되지 않도록 애너테이션을 붙이지 않는다. 실제로 잠그지 않고, 잠근 조직·기사와 그 시점까지 저장소가 기사의
 * 활성 작업을 조회한 횟수를 기록해 "읽기 전에 잠갔는지"를 확인하게 한다. 실패를 지정하면 잠금 시도가 그 예외로 끝난다. 잠금 시 동작을 지정하면 잠글 때
 * 실행해, 잠금을 기다리는 동안 다른 요청이 먼저 커밋한 상황을 흉내 낸다.
 */
public class FakeLockTechnicianSchedulePort implements LockTechnicianSchedulePort {

    private final FakeWorkRepository workRepository;
    private final List<Lock> locks = new ArrayList<>();
    private RuntimeException failure;
    private Runnable whileWaiting = () -> {};

    public FakeLockTechnicianSchedulePort(FakeWorkRepository workRepository) {
        this.workRepository = workRepository;
    }

    @Override
    public void lock(OrganizationId organizationId, MembershipId technicianId) {
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(technicianId, "technicianId must not be null");
        if (failure != null) {
            throw failure;
        }
        whileWaiting.run();
        locks.add(new Lock(organizationId, technicianId, workRepository.activeByTechnicianQueryCount()));
    }

    /** 잠글 때 주어진 동작을 실행한다. 잠금을 기다리는 동안 앞선 요청이 저장·커밋한 것처럼 저장소를 바꾸는 데 쓴다. */
    public void whileWaiting(Runnable action) {
        this.whileWaiting = Objects.requireNonNull(action, "action must not be null");
    }

    /** 이후 잠금 시도가 주어진 예외로 실패하게 한다(대기 한도 초과 등). */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public List<Lock> locks() {
        return List.copyOf(locks);
    }

    /** 잠근 조직·기사와, 잠글 때까지 기사의 활성 작업을 조회한 횟수. */
    public record Lock(OrganizationId organizationId, MembershipId technicianId, int activeQueriesBefore) {}
}
