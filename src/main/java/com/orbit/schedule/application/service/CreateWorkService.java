package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.SiteCoordinatesPort;

/**
 * 작업 등록. 현장 기본주소의 좌표를 트랜잭션 밖에서 먼저 구한 뒤({@link SiteLookup}) 등록 규칙·저장은 {@link WorkRegistration}의 트랜잭션에서 한다. 외부
 * 지오코딩 동안 DB 연결을 잡지 않으려고 메서드 트랜잭션을 두지 않는다. 조직의 관리자가 아닌 요청은 조회 전에 거부해(트랜잭션 안에서도 다시 확인) 외부 호출을
 * 일으키지 못하게 한다. 작업 입력은 트랜잭션 안에서 검증하므로 입력 오류로 거부되는 요청도 조회할 수 있다.
 */
@Service
public class CreateWorkService implements CreateWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final SiteCoordinatesPort siteCoordinatesPort;
    private final WorkRegistration registration;

    public CreateWorkService(
            LoadActorPort loadActorPort, SiteCoordinatesPort siteCoordinatesPort, WorkRegistration registration) {
        this.loadActorPort = loadActorPort;
        this.siteCoordinatesPort = siteCoordinatesPort;
        this.registration = registration;
    }

    @Override
    public CreatedWorkInfo create(CreateWorkCommand command) {
        OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        return registration.register(command, SiteLookup.lookUp(siteCoordinatesPort, command.customerAddress()));
    }
}
