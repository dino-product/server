package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.media.MediaFiles;
import com.orbit.schedule.application.port.in.command.IssueSitePhotoUploadUseCase;
import com.orbit.schedule.application.port.in.command.dto.IssueSitePhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.domain.ManagerActor;

/**
 * 현장 사진 업로드 주소 발급. 그 조직의 총관리자·직원이 작업 등록·기본정보 수정에 붙일 사진을 올린다([작업] §4). 작업 등록 전에도 올리므로 작업을 확인하지 않고, 사진을
 * 붙일 때 그 작업을 고칠 수 있는지 확인한다. 장당 용량은 설정({@link SitePhotoProperties})을 따른다. 업로드 대기 기록을 남기므로 쓰기 트랜잭션이며, 서명은 저장소를
 * 부르지 않는다. 오류 확인 순서는 schedule 지침의 공통 순서(요청자 → 요청 값)를 따른다.
 */
@Service
public class IssueSitePhotoUploadService implements IssueSitePhotoUploadUseCase {

    private final LoadActorPort loadActorPort;
    private final MediaFiles mediaFiles;
    private final SitePhotoProperties properties;

    public IssueSitePhotoUploadService(
            LoadActorPort loadActorPort, MediaFiles mediaFiles, SitePhotoProperties properties) {
        this.loadActorPort = loadActorPort;
        this.mediaFiles = mediaFiles;
        this.properties = properties;
    }

    @Override
    @Transactional
    public PhotoUploadInfo issue(IssueSitePhotoUploadCommand command) {
        ManagerActor actor =
                OrganizationActors.requireManager(loadActorPort, command.accountId(), command.organizationId());
        WorkPhotos.requireUploadInput(command.contentType(), command.sizeBytes());
        return WorkPhotos.issue(
                mediaFiles,
                actor.organizationId(),
                command.accountId(),
                WorkPhotos.SITE_PHOTO,
                properties.maxBytes(),
                command.contentType(),
                command.sizeBytes());
    }
}
