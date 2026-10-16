package com.orbit.schedule.application.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.schedule.application.port.in.query.GetWithdrawalBlockingWorksUseCase;
import com.orbit.schedule.application.port.in.query.dto.GetWithdrawalBlockingWorksQuery;
import com.orbit.schedule.application.port.in.query.dto.WithdrawalBlockingWorksInfo;
import com.orbit.schedule.application.port.out.AccountTechnicianContract;
import com.orbit.schedule.application.port.out.AccountTechnicianContractsPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;

/**
 * 탈퇴 차단 판정. 계정의 활성 기사 계약마다 그 기사에게 지금 배정된 작업 중 작업중과 응답 전(배정됨) 작업을 세어 합친다. 작업전·완료 작업은 막지 않는다(작업전은
 * 탈퇴 뒤 대기함으로 돌린다). 비활성 계약은 이미 소속이 끝났으므로 남은 작업중 작업이 있어도 세지 않는다.
 *
 * <p>탈퇴 요청자의 트랜잭션 안에서 상태를 바꾸지 않고 읽기만 한다. 기사를 잠그지 않으므로 판정과 탈퇴 커밋 사이에 새로 생긴 배정은 막지 못하지만, 그 배정은 탈퇴 뒤처리
 * ({@link ReleaseWithdrawnTechnicianWorksService})가 함께 대기함으로 돌린다. 반면 판정 뒤 탈퇴 커밋 전에 작업 시작·관리자 상태 정정이 커밋되면 탈퇴한
 * 계정에 작업중 작업이 남는다. 여기서 기사를 잠가도 이미 들어온 요청은 막지 못하므로 남는 위험으로 두고, 해소 방향은 정하지 않았다(schedule 문서의 동시성).
 */
@Service
public class GetWithdrawalBlockingWorksService implements GetWithdrawalBlockingWorksUseCase {

    private final AccountTechnicianContractsPort accountTechnicianContractsPort;
    private final WorkRepository workRepository;

    public GetWithdrawalBlockingWorksService(
            AccountTechnicianContractsPort accountTechnicianContractsPort, WorkRepository workRepository) {
        this.accountTechnicianContractsPort = accountTechnicianContractsPort;
        this.workRepository = workRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public WithdrawalBlockingWorksInfo get(GetWithdrawalBlockingWorksQuery query) {
        Long accountId = Objects.requireNonNull(query.accountId(), "accountId must not be null");
        int inProgress = 0;
        int pendingAcceptance = 0;
        for (AccountTechnicianContract contract : accountTechnicianContractsPort.listByAccount(accountId)) {
            if (!contract.active()) {
                continue;
            }
            for (Work work :
                    workRepository.listActiveByTechnician(contract.organizationId(), contract.technicianId())) {
                if (work.status() == WorkStatus.IN_PROGRESS) {
                    inProgress++;
                } else if (work.status() == WorkStatus.PENDING_ACCEPTANCE) {
                    pendingAcceptance++;
                }
            }
        }
        return new WithdrawalBlockingWorksInfo(inProgress, pendingAcceptance);
    }
}
