package com.orbit.schedule.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.command.ReleaseWithdrawnTechnicianWorksUseCase;
import com.orbit.schedule.application.port.in.command.dto.ReleaseWithdrawnTechnicianWorksCommand;
import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;
import com.orbit.schedule.application.port.out.LockTechnicianSchedulePort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 탈퇴 뒤처리. 탈퇴한 계정의 모든 기사 계약(organization이 같은 탈퇴로 먼저 비활성화했을 수 있어 비활성 포함)에서 배정됨·작업전 작업을 처리자 없이 대기함으로
 * 돌린다. 작업중·완료 작업은 그대로 둔다. 그 기사 계약을 미리 입력한 담당기사로 둔 대기함 작업에서는 담당기사만 비운다([스케줄·배정] §9). 이미 대기함인 작업은 활성 작업
 * 조회에, 담당기사를 비운 작업은 미리 입력한 담당기사 조회에 잡히지 않으므로 같은 탈퇴를 다시 처리해도 결과가 같다(재전송 멱등). 한 작업이라도 돌릴 수
 * 없으면(배정 이력 시각 역전 등) 요청자의 입력 오류가 아니므로 오류 코드로 바꾸지 않고 도메인 예외를 그대로 전파해 전체를 롤백한다.
 *
 * <p>배정·재배정·일부만 입력한 등록과 경합하지 않도록 계약마다 기사를 잠근 뒤 그 기사의 작업을 읽는다. 한 트랜잭션에서 여러 기사를 잠그므로 조직·기사 계약 ID 오름차순으로
 * 잠근다. 다른 유즈케이스는 기사 하나만 잠그고 한 계정은 한 조직과 기사 계약을 하나만 맺으므로, 이 순서로 서로 기다리는 고리가 생기지 않는다. 잠금 대기
 * 한도를 넘기면 409(SCHEDULE-006)로 실패하고 전체가 롤백돼 다음 재전송을 기다린다.
 *
 * <p>남는 위험: organization의 계약 비활성화는 같은 탈퇴 이벤트의 별도 리스너라 순서가 없다. 이 반환이 비활성화보다 먼저 끝나면 그 사이 탈퇴한 기사에게 생긴 배정과
 * 일부만 입력한 등록의 미리 입력한 담당기사가 남고, 반환은 이미 성공했으므로 재전송으로도 처리되지 않는다. 해소 방향(계정 기사 계약 계약 HM-321의 형태, 비활성화와의 순서 보장 등)은 정하지 않았다.
 */
@Service
public class ReleaseWithdrawnTechnicianWorksService implements ReleaseWithdrawnTechnicianWorksUseCase {

    private static final Comparator<AccountTechnicianContract> LOCK_ORDER = Comparator.comparing(
                    (AccountTechnicianContract contract) ->
                            contract.organizationId().value())
            .thenComparing(contract -> contract.technicianId().value());

    private final AccountTechnicianContractsPort accountTechnicianContractsPort;
    private final WorkRepository workRepository;
    private final LockTechnicianSchedulePort lockTechnicianSchedulePort;
    private final Clock clock;

    public ReleaseWithdrawnTechnicianWorksService(
            AccountTechnicianContractsPort accountTechnicianContractsPort,
            WorkRepository workRepository,
            LockTechnicianSchedulePort lockTechnicianSchedulePort,
            Clock clock) {
        this.accountTechnicianContractsPort = accountTechnicianContractsPort;
        this.workRepository = workRepository;
        this.lockTechnicianSchedulePort = lockTechnicianSchedulePort;
        this.clock = clock;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(ReleaseWithdrawnTechnicianWorksCommand command) {
        Long accountId = Objects.requireNonNull(command.accountId(), "accountId must not be null");
        List<AccountTechnicianContract> contracts = accountTechnicianContractsPort.listByAccount(accountId).stream()
                .sorted(LOCK_ORDER)
                .toList();
        Instant now = clock.instant();
        for (AccountTechnicianContract contract : contracts) {
            TechnicianScheduleLocks.lock(
                    lockTechnicianSchedulePort, contract.organizationId(), contract.technicianId());
            for (Work work :
                    workRepository.listActiveByTechnician(contract.organizationId(), contract.technicianId())) {
                if (work.status() == WorkStatus.PENDING_ACCEPTANCE || work.status() == WorkStatus.ACCEPTED) {
                    work.releaseForWithdrawal(now);
                    workRepository.save(work);
                }
            }
            workRepository.listBacklogPlannedForTechnician(contract.organizationId(), contract.technicianId()).stream()
                    .sorted(Comparator.comparing(work -> work.id().orElseThrow().value()))
                    .forEach(work -> {
                        work.clearPlannedTechnician();
                        workRepository.save(work);
                    });
        }
    }
}
