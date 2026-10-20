package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
import com.orbit.schedule.application.port.in.command.CreateWorkUseCase;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.CreatedWorkInfo;

/**
 * 작업 등록. 요청한 조직의 총관리자·직원이 작업을 만든다. 작업명만 필수이고 작업 유형·고객·결제 정보는 비워도 된다. 담당기사·시작시각·예상소요시간을 모두
 * 주면 등록과 함께 배정하고(배정과 같은 규칙), 모두 비우면 대기함에 둔다. 일부만 주면 입력한 값을 미리 입력한 일정으로 남겨 대기함에 두며, 그중 담당기사가
 * 있으면 같은 조직의 활성 기사 계약이어야 한다. 그 담당기사를 잠근 뒤 확인해, 같은 기사를 잠그고 미리 입력한 담당기사를 비우는 대기함 반환과 한 줄로
 * 선다([스케줄·배정] §9). 함께 배정하려던 일정이 겹치는데 확인하지 않았으면 작업을 만들지 않는다. 확인 뒤 다시 보낸 요청이 작업을 한 번만 만들게 하기 위해서다. 오류
 * 확인 순서는 schedule 지침의 공통 순서를 따른다. 작업 유형을 지정하면 같은 조직에서 선택할 수 있는 유형이어야 한다. 현장 사진은 요청자가 올린 파일의 식별자로 최대
 * 6장까지 받으며 다른 입력과 함께 검증한다(업로드를 마치지 않았거나 남의 사진이면 400 SCHEDULE-022, 이미지가 아니면 SCHEDULE-020, 밝힌 크기보다 크면
 * SCHEDULE-021). 등록과 함께 배정해 저장했으면 {@link com.orbit.schedule.WorkAssigned}를 발행하고, 대기함에 둔 등록은 발행하지 않는다.
 *
 * <p>사진 저장소 확인·복사는 DB 연결을 붙잡지 않도록 트랜잭션을 열기 전에 하고(저장소 장애는 503 SCHEDULE-023), 업무 처리와 사진 연결은
 * {@link CreateWorkTransaction}이 한 트랜잭션에서 한다(ADR-006). 그래서 이 메서드에는 트랜잭션이 없다.
 */
@Service
public class CreateWorkService implements CreateWorkUseCase {

    private final MediaFiles mediaFiles;
    private final CreateWorkTransaction transaction;

    public CreateWorkService(MediaFiles mediaFiles, CreateWorkTransaction transaction) {
        this.mediaFiles = mediaFiles;
        this.transaction = transaction;
    }

    @Override
    public CreatedWorkInfo create(CreateWorkCommand command) {
        StagedFiles sitePhotos = WorkPhotos.stage(
                mediaFiles,
                command.accountId(),
                command.organizationId(),
                WorkPhotos.SITE_PHOTO,
                command.sitePhotoIds(),
                null);
        return transaction.create(command, sitePhotos);
    }
}
