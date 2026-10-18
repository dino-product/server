package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.StartWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.StartWorkCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.AssignmentHistory;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkSchedule;
import com.orbit.shared.error.BusinessException;

/**
 * 작업 시작. 담당 기사 본인이 화면에서 본 최신 배정의 수락된 작업을 작업중으로 바꾸고 지금 시각을 시작 시각으로 남긴다. 배정이 그대로인 채 이미 시작한 작업(완료
 * 포함)에 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공한다. 그 배정 일정의 시작 가능 시간({@link WorkSchedule#isTooEarlyToStart}·{@link
 * WorkSchedule#isTooLateToStart})을 벗어나면 이를 때는 409(SCHEDULE-017), 늦을 때는 409(SCHEDULE-018)다. 같은 기사에게 작업중인 다른 작업이
 * 있어도 시작할 수 있어 기사의 다른 작업을 읽지 않으므로 기사를 잠그지 않는다. 같은 작업에 동시에 들어온 다른 요청(같은 시작의 중복 전송, 관리자의 재배정·일정
 * 변경·해제·취소, 대기함 반환)과의 경합은 작업 버전 검사(HM-236)로 뒤 요청을 실패시키고, 겹친 뒤 요청은 아직 500으로 실패한다. 오류 확인 순서는 schedule
 * 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class StartWorkService implements StartWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final Clock clock;

    public StartWorkService(LoadActorPort loadActorPort, WorkRepository workRepository, Clock clock) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void start(StartWorkCommand command) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireVisible(work, technician);
        int assignmentNumber = AssignedTechnicians.requireAssignmentNumber(command.assignmentNumber());
        AssignmentHistory assignment =
                AssignedTechnicians.requireCurrentAssignmentOf(work, technician, assignmentNumber);
        if (work.startedAt().isPresent() && assignment.ending().isEmpty()) {
            return;
        }

        Instant now = clock.instant();
        DomainRuleViolations.run(() -> work.start(now));
        // 상태·시각 순서를 확인한 뒤 판정한다. 거부하면 바꾼 작업을 저장하지 않고 버린다(저장소는 조회 사본을 돌려준다).
        requireStartableAt(assignment.schedule(), now);
        workRepository.save(work);
    }

    private static void requireStartableAt(WorkSchedule schedule, Instant now) {
        if (schedule.isTooEarlyToStart(now)) {
            throw new BusinessException(ScheduleErrorCode.TOO_EARLY_TO_START);
        }
        if (schedule.isTooLateToStart(now)) {
            throw new BusinessException(ScheduleErrorCode.START_TIME_PASSED);
        }
    }
}
