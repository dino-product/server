package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbit.media.MediaFiles;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.IssueCompletionPhotoUploadUseCase;
import com.orbit.schedule.application.port.in.command.dto.IssueCompletionPhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;
import com.orbit.schedule.application.port.out.LoadActorPort;
import com.orbit.schedule.application.port.out.WorkRepository;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.TechnicianActor;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

/**
 * 완료보고 사진 업로드 주소 발급. 담당 기사 본인이 자기 작업중 작업에만 올린다([현장 수행] §6). 장당 용량은 {@link CompletionReport#MAX_PHOTO_BYTES}다. 기사가 볼 수
 * 없는 작업은 404(SCHEDULE-001), 형식·크기를 확인한 뒤 작업중이 아니면 409(SCHEDULE-002)이고, 같은 트랜잭션의 발급 기록도 롤백된다.
 * 배정 순번은 받지 않으며 완료보고 제출이 확인한다. 업로드 대기 기록을 남기므로 쓰기 트랜잭션이다. 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 */
@Service
public class IssueCompletionPhotoUploadService implements IssueCompletionPhotoUploadUseCase {

    private final LoadActorPort loadActorPort;
    private final WorkRepository workRepository;
    private final MediaFiles mediaFiles;

    public IssueCompletionPhotoUploadService(
            LoadActorPort loadActorPort, WorkRepository workRepository, MediaFiles mediaFiles) {
        this.loadActorPort = loadActorPort;
        this.workRepository = workRepository;
        this.mediaFiles = mediaFiles;
    }

    @Override
    @Transactional
    public PhotoUploadInfo issue(IssueCompletionPhotoUploadCommand command) {
        TechnicianActor technician =
                OrganizationActors.requireTechnician(loadActorPort, command.accountId(), command.organizationId());
        Work work = OrganizationWorks.require(workRepository, technician.organizationId(), command.workId());
        AssignedTechnicians.requireVisible(work, technician);
        WorkPhotos.requireUploadInput(command.contentType(), command.sizeBytes());
        // 형식·크기(요청 값)는 media가 발급하며 확인하므로 발급한 뒤에 상태를 본다. 작업중이 아니면 이 트랜잭션이 롤백돼 업로드 대기 기록이 남지 않고,
        // 서명한 주소는 돌려주지 않는다(서명은 저장소를 부르지 않음).
        PhotoUploadInfo upload = WorkPhotos.issue(
                mediaFiles,
                technician.organizationId(),
                command.accountId(),
                WorkPhotos.COMPLETION_PHOTO,
                CompletionReport.MAX_PHOTO_BYTES,
                command.contentType(),
                command.sizeBytes());
        if (work.status() != WorkStatus.IN_PROGRESS) {
            throw new BusinessException(ScheduleErrorCode.INVALID_WORK_STATE);
        }
        return upload;
    }
}
