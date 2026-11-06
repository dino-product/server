package com.orbit.schedule.application.service;

import org.springframework.stereotype.Service;

import com.orbit.media.MediaFiles;
import com.orbit.media.StagedFiles;
import com.orbit.schedule.application.port.in.command.SubmitCompletionReportUseCase;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;

/**
 * 완료보고 제출. 담당 기사 본인이 화면에서 본 최신 배정의 작업중인 작업에 보고를 붙여 완료로 바꾸고 지금 시각을 완료 시각으로 남긴다. 보고 저장과 완료 전환은 한 번의 저장으로 함께 성공·실패한다. 이미
 * 같은 보고로 완료한 작업에 다시 보내면(중복 전송) 아무것도 바꾸지 않고 성공하고, 다른 보고면 409(SCHEDULE-002)다. 완료는 기사의 활성 작업을 줄이기만 하고 대기함 반환이 바꾸는
 * 작업(배정됨·작업전)을 건드리지 않으므로, 수락·거절·시작과 달리 기사를 잠그지 않는다. 관리자의 취소·기본정보 수정·같은 보고의 다른 제출과 같은 순간에 들어와 저장 자리에서 충돌하면, 사진은 다시
 * 확인하지 않고 트랜잭션 부분만 새 트랜잭션에서 한 번 다시 실행해 다시 읽은 상태로 판정한다(같은 보고면 중복 전송 성공, 취소됐으면 409 SCHEDULE-002, {@link
 * WorkVersions#retryOnConflict}). 오류 확인 순서는 schedule 지침의 기사 유즈케이스 순서를 따른다.
 *
 * <p>사진은 배정된 기사 본인이 올린 사진만 받고(아니면 400 SCHEDULE-022), 이미지가 아니면 SCHEDULE-020, 밝힌 크기보다 크면 SCHEDULE-021이다. 사진 저장소
 * 확인·복사는 트랜잭션을 열기 전에 하고(저장소 장애는 503 SCHEDULE-023), 업무 처리와 사진 연결은 {@link SubmitCompletionReportTransaction}이 한 트랜잭션에서
 * 한다(ADR-007). 그래서 이 메서드에는 트랜잭션이 없다.
 */
@Service
public class SubmitCompletionReportService implements SubmitCompletionReportUseCase {

    private final MediaFiles mediaFiles;
    private final SubmitCompletionReportTransaction transaction;

    public SubmitCompletionReportService(MediaFiles mediaFiles, SubmitCompletionReportTransaction transaction) {
        this.mediaFiles = mediaFiles;
        this.transaction = transaction;
    }

    @Override
    public void submit(SubmitCompletionReportCommand command) {
        StagedFiles photos = WorkPhotos.stageCompletionPhotos(
                mediaFiles,
                command.accountId(),
                command.organizationId(),
                command.beforePhotos(),
                command.afterPhotos(),
                command.workId());
        // 다시 실행해도 사진은 다시 확인·복사하지 않는다. 연결은 커밋된 시도에서 한 번만 한다.
        WorkVersions.retryOnConflict(() -> transaction.submit(command, photos));
    }
}
