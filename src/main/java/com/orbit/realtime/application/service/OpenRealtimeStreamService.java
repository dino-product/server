package com.orbit.realtime.application.service;

import org.springframework.stereotype.Service;

import com.orbit.organization.OrganizationMemberLookup;
import com.orbit.organization.StaffMember;
import com.orbit.realtime.application.error.RealtimeErrorCode;
import com.orbit.realtime.application.port.in.command.OpenRealtimeStreamUseCase;
import com.orbit.realtime.application.port.in.command.dto.OpenRealtimeStreamCommand;
import com.orbit.realtime.application.port.out.RealtimeConnectionPort;
import com.orbit.shared.error.BusinessException;

/**
 * 실시간 연결 열기. 발주사의 활성 직원(총관리자 포함)만 연다. 기사·비활성·다른 발주사 계정은 403(REALTIME-001)이다. 인스턴스 연결 수 상한에 닿았거나 인스턴스가
 * 종료 중이면 503(REALTIME-002)으로 거부해 화면이 잠시 뒤 다른 인스턴스로 다시 연결하게 한다.
 *
 * <p>트랜잭션을 두지 않는다. 권한 확인은 organization 조회가 자기 읽기 트랜잭션으로 끝내고 연결은 인스턴스 메모리에만 남긴다. 그래서 연결이 이어지는 동안 DB
 * 연결을 잡지 않는다(요청 동안 영속성 컨텍스트를 열어 두지 않는 {@code spring.jpa.open-in-view: false} 전제). 연결을 연 뒤 소속이 바뀌어도 이미 열린 연결은
 * 수명이 끝나 다시 연결할 때까지 유지된다.
 */
@Service
public class OpenRealtimeStreamService implements OpenRealtimeStreamUseCase {

    private final OrganizationMemberLookup memberLookup;
    private final RealtimeConnectionPort connectionPort;

    public OpenRealtimeStreamService(OrganizationMemberLookup memberLookup, RealtimeConnectionPort connectionPort) {
        this.memberLookup = memberLookup;
        this.connectionPort = connectionPort;
    }

    @Override
    public void open(OpenRealtimeStreamCommand command) {
        boolean staff = memberLookup
                .findActiveMember(command.accountId(), command.organizationId())
                .filter(StaffMember.class::isInstance)
                .isPresent();
        if (!staff) {
            throw new BusinessException(RealtimeErrorCode.NOT_OFFICE_MEMBER);
        }
        if (!connectionPort.register(
                command.accountId(), command.organizationId(), command.clientType(), command.channel())) {
            throw new BusinessException(RealtimeErrorCode.CONNECTION_UNAVAILABLE);
        }
    }
}
