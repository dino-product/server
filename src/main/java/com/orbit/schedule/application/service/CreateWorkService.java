package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.SiteCoordinatesPort;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원이 작업을 만든다. 작업명만 필수이고 작업 유형·고객·결제 정보는 비워도 된다. 담당기사·시작시각·예상소요시간을 모두
 * 주면 등록과 함께 배정하고(배정과 같은 규칙), 모두 비우면 대기함에 둔다. 일부만 주면 입력한 값을 미리 입력한 일정으로 남겨 대기함에 두며, 그중 담당기사가
 * 있으면 같은 조직의 활성 기사 계약이어야 한다. 그 담당기사를 잠근 뒤 확인해, 같은 기사를 잠그고 미리 입력한 담당기사를 비우는 대기함 반환과 한 줄로
 * 선다([스케줄·배정] §9). 함께 배정하려던 일정이 겹치는데 확인하지 않았으면 작업을 만들지 않는다. 확인 뒤 다시 보낸 요청이 작업을 한 번만 만들게 하기 위해서다. 오류
 * 확인 순서는 schedule 지침의 공통 순서를 따른다. 작업 유형을 지정하면 같은 조직에서 선택할 수 있는 유형이어야 한다. 현장 사진은 요청자가 올린 파일의 식별자로 최대
 * 6장까지 받으며 다른 입력과 함께 검증한다(업로드를 마치지 않았거나 남의 사진이면 400 SCHEDULE-022, 이미지가 아니면 SCHEDULE-020, 밝힌 크기보다 크면
 * SCHEDULE-021). 등록과 함께 배정해 저장했으면 {@link com.orbit.schedule.WorkAssigned}를 발행하고, 저장한 모든 등록(대기함에 둔 등록 포함)은 이어서
 * {@link com.orbit.schedule.WorkChanged}를 발행한다.
 *
 * <p>사진 저장소 확인·복사와 현장 기본주소의 좌표 조회({@link SiteLookup})는 DB 연결을 붙잡지 않도록 트랜잭션을 열기 전에 하고(저장소 장애는 503
 * SCHEDULE-023, 좌표를 구하지 못하면 좌표 없이 저장), 업무 처리·사진 연결·좌표 반영은 {@link CreateWorkTransaction}이 한 트랜잭션에서 한다(ADR-007).
 * 그래서 이 메서드에는 트랜잭션이 없다. 조직의 관리자가 아닌 요청은 저장소·좌표 조회 전에 거부해(트랜잭션 안에서도 다시 확인) 외부 호출을 일으키지 못하게
 * 한다. 작업 입력은 트랜잭션 안에서 검증하므로 입력 오류로 거부되는 요청도 조회할 수 있다.
 */
@Service
public class CreateWorkService implements CreateWorkUseCase {

    private final LoadActorPort loadActorPort;
    private final SiteCoordinatesPort siteCoordinatesPort;
    private final MediaFiles mediaFiles;
    private final CreateWorkTransaction transaction;

    public CreateWorkService(
            LoadActorPort loadActorPort,
            SiteCoordinatesPort siteCoordinatesPort,
            MediaFiles mediaFiles,
            CreateWorkTransaction transaction) {
        this.loadActorPort = loadActorPort;
        this.siteCoordinatesPort = siteCoordinatesPort;
        this.mediaFiles = mediaFiles;
        this.transaction = transaction;
    }

    @Override
    public CreatedWorkInfo create(CreateWorkCommand command) {
        OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        StagedFiles sitePhotos = WorkPhotos.stageSitePhotos(
                mediaFiles, command.accountId(), command.organizationId(), command.sitePhotoIds(), null);
        SiteLookup site = SiteLookup.lookUp(siteCoordinatesPort, command.customerAddress());
        return transaction.create(command, sitePhotos, site);
    }
}
