package com.orbit.schedule.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.SiteCoordinatesPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.ManagerActor;
import com.orbit.schedule.domain.SiteAddress;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;

/**
 * 작업 기본정보 수정. 현장 좌표가 필요하면 트랜잭션 밖에서 먼저 구한 뒤({@link SiteLookup}) 수정 규칙·저장은 {@link WorkDetailsChange}의 트랜잭션에서
 * 한다. 외부 지오코딩 동안 DB 연결을 잡지 않으려고 메서드 트랜잭션을 두지 않는다. 조직의 관리자가 아닌 요청은 조회 전에 거부한다(트랜잭션 안에서도 다시 확인).
 * 이어서 지금 작업을 저장소의 읽기 트랜잭션으로 미리 읽어, 트랜잭션 안에서 거부될 요청(식별자 형식 오류·없는 작업·다른 조직·등록자도 총관리자도 아님·완료나
 * 취소된 작업)이거나 기본주소가 그대로이고 좌표가 있으면(이전 좌표를 이어받음) 조회하지 않는다.
 */
@Service
public class UpdateWorkDetailsService implements UpdateWorkDetailsUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final SiteCoordinatesPort siteCoordinatesPort;
    private final WorkDetailsChange change;

    public UpdateWorkDetailsService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            SiteCoordinatesPort siteCoordinatesPort,
            WorkDetailsChange change) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.siteCoordinatesPort = siteCoordinatesPort;
        this.change = change;
    }

    @Override
    public void update(UpdateWorkDetailsCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        change.change(command, lookUpSite(command, actor));
    }

    private SiteLookup lookUpSite(UpdateWorkDetailsCommand command, ManagerActor actor) {
        Optional<String> requested = SiteLookup.lookupAddress(command.customerAddress());
        if (requested.isEmpty()) {
            return SiteLookup.none();
        }
        boolean needsLookup = currentWork(command, actor)
                .filter(work -> OrganizationActors.isRegistrarOrOwner(actor, work))
                .filter(work -> !work.status().isTerminal())
                .map(work -> work.customerInfo()
                        .address()
                        .filter(address -> address.baseAddress().equals(requested))
                        .flatMap(SiteAddress::coordinates)
                        .isEmpty())
                .orElse(false);
        return needsLookup ? SiteLookup.lookUp(siteCoordinatesPort, requested.get()) : SiteLookup.none();
    }

    private Optional<Work> currentWork(UpdateWorkDetailsCommand command, ManagerActor actor) {
        WorkId workId;
        try {
            workId = new WorkId(command.workId());
        } catch (IllegalArgumentException exception) {
            // 식별자 형식 오류는 트랜잭션 안의 공통 순서가 알린다.
            return Optional.empty();
        }
        return workRepository.findInOrganization(actor.organizationId(), workId);
    }
}
