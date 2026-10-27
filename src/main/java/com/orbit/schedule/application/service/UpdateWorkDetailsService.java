package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
import com.orbit.schedule.application.port.in.command.UpdateWorkDetailsUseCase;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;

/**
 * 작업 기본정보 수정. 작업명·작업 유형·고객정보·결제정보·현장 사진을 한꺼번에 교체하고 상태·배정은 그대로 둔다. 오류 확인 순서는 schedule 지침의 공통 순서를
 * 따른다. 작업 유형을 바꾸면 같은 조직에서 선택할 수 있는 유형이어야 하고, 작업중에는 바꿀 수 없다. 현장 사진은 이미 그 작업에 붙은 사진(다른 관리자가 올렸어도)과 요청자가
 * 새로 올린 사진만 받고(아니면 400 SCHEDULE-022), 빠진 사진은 만료로 바꿔 저장소 수명 주기 규칙이 지우게 한다.
 *
 * <p>사진 저장소 확인·복사는 트랜잭션을 열기 전에 하고(저장소 장애는 503 SCHEDULE-023), 업무 처리와 사진 연결은 {@link UpdateWorkDetailsTransaction}이 한
 * 트랜잭션에서 한다(ADR-006). 그래서 이 메서드에는 트랜잭션이 없다.
 */
@Service
public class UpdateWorkDetailsService implements UpdateWorkDetailsUseCase {

    private final MediaFiles mediaFiles;
    private final UpdateWorkDetailsTransaction transaction;

    public UpdateWorkDetailsService(MediaFiles mediaFiles, UpdateWorkDetailsTransaction transaction) {
        this.mediaFiles = mediaFiles;
        this.transaction = transaction;
    }

    @Override
    public void update(UpdateWorkDetailsCommand command) {
        StagedFiles sitePhotos = WorkPhotos.stageSitePhotos(
                mediaFiles, command.accountId(), command.organizationId(), command.sitePhotoIds(), command.workId());
        transaction.update(command, sitePhotos);
    }
}
