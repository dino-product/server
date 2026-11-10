package com.orbit.schedule.application.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
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
 * 작업 기본정보 수정. 작업명·작업 유형·고객정보·결제정보·현장 사진을 한꺼번에 교체하고 상태·배정은 그대로 둔다. 오류 확인 순서는 schedule 지침의 공통 순서를
 * 따른다. 작업 유형을 바꾸면 같은 조직에서 선택할 수 있는 유형이어야 하고, 작업중에는 바꿀 수 없다. 현장 사진은 이미 그 작업에 붙은 사진(다른 관리자가 올렸어도)과 요청자가
 * 새로 올린 사진만 받고(아니면 400 SCHEDULE-022), 빠진 사진은 만료로 바꿔 저장소 수명 주기 규칙이 지우게 한다. 현장 기본주소가 그대로면 이전 좌표를 이어받는다.
 *
 * <p>사진 저장소 확인·복사와 현장 좌표 조회({@link SiteLookup})는 트랜잭션을 열기 전에 하고(저장소 장애는 503 SCHEDULE-023, 좌표를 구하지 못하면
 * 좌표 없이 저장), 업무 처리·사진 연결·좌표 반영은 {@link UpdateWorkDetailsTransaction}이 한 트랜잭션에서 한다(ADR-007). 그래서 이 메서드에는 트랜잭션이
 * 없다. 조직의 관리자가 아닌 요청은 저장소·좌표 조회 전에 거부한다(트랜잭션 안에서도 다시 확인). 좌표는 지금 작업을 저장소의 읽기 트랜잭션으로 미리 읽어,
 * 트랜잭션 안에서 거부될 요청(식별자 형식 오류·없는 작업·다른 조직·등록자도 총관리자도 아님·완료나 취소된 작업)이거나 기본주소가 그대로이고 좌표가 있으면(이전 좌표를
 * 이어받음) 조회하지 않는다. 저장 자리에서 다른 변경과 충돌하면 사진·좌표는 다시 구하지 않고 트랜잭션 부분만 새 트랜잭션에서 한 번 다시 실행해, 다시 읽은
 * 작업에 고쳐 저장한다([작업] §5 나중 저장이 이김, {@link WorkVersions#retryOnConflict}).
 */
@Service
public class UpdateWorkDetailsService implements UpdateWorkDetailsUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final SiteCoordinatesPort siteCoordinatesPort;
    private final MediaFiles mediaFiles;
    private final UpdateWorkDetailsTransaction transaction;

    public UpdateWorkDetailsService(
            LoadActorPort loadActorPort,
            WorkRepository workRepository,
            SiteCoordinatesPort siteCoordinatesPort,
            MediaFiles mediaFiles,
            UpdateWorkDetailsTransaction transaction) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.siteCoordinatesPort = siteCoordinatesPort;
        this.mediaFiles = mediaFiles;
        this.transaction = transaction;
    }

    @Override
    public void update(UpdateWorkDetailsCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        StagedFiles sitePhotos = WorkPhotos.stageSitePhotos(
                mediaFiles, command.accountId(), command.organizationId(), command.sitePhotoIds(), command.workId());
        SiteLookup site = lookUpSite(command, actor);
        WorkVersions.retryOnConflict(() -> transaction.update(command, sitePhotos, site));
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
