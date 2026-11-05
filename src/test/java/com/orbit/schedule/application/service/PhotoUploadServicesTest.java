package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.OTHER_TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.UNKNOWN_WORK_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.orbit.media.MediaStorageUnavailableException;
import com.orbit.media.UploadNotAllowedException;
import com.orbit.media.UploadRequest;
import com.orbit.media.UploadSpec;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.IssueCompletionPhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.IssueSitePhotoUploadCommand;
import com.orbit.schedule.application.port.in.command.dto.PhotoUploadInfo;
import com.orbit.schedule.application.service.fake.FakeMediaFiles;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;
import com.orbit.shared.error.BusinessException;

@DisplayName("사진 업로드 주소 발급")
class PhotoUploadServicesTest {

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();

    @Nested
    @DisplayName("현장 사진")
    class Site {

        private IssueSitePhotoUploadService service(Long maxBytes) {
            return new IssueSitePhotoUploadService(
                    fixture.actorPort, fixture.mediaFiles, new SitePhotoProperties(maxBytes));
        }

        @ParameterizedTest
        @EnumSource(
                value = ActorRole.class,
                names = {"OWNER", "STAFF"})
        @DisplayName("총관리자·직원은 현장 사진 용도·설정한 최대 크기로 발급받는다")
        void managerIssues(ActorRole role) {
            fixture.givenManager(role);

            PhotoUploadInfo info = service(1_000L).issue(command("image/jpeg", 500L));

            assertThat(info.fileId()).isEqualTo("file-1");
            assertThat(info.expiresAt()).isEqualTo(FakeMediaFiles.EXPIRES_AT);
            assertThat(fixture.mediaFiles.uploadRequests())
                    .containsExactly(new UploadRequest(
                            ORGANIZATION_ID.value(),
                            ACCOUNT_ID,
                            new UploadSpec("WORK_SITE_PHOTO", 1_000L),
                            "image/jpeg",
                            500L));
        }

        @Test
        @DisplayName("장당 용량 설정이 없으면 저장소 한도만 적용한다(JOB-003 미정)")
        void withoutLimitUsesStorageMax() {
            fixture.givenManager();

            service(null).issue(command("image/png", 500L));

            assertThat(fixture.mediaFiles.uploadRequests().getFirst().spec().maxBytes())
                    .isEqualTo(UploadSpec.STORAGE_MAX_BYTES);
        }

        @Test
        @DisplayName("기사는 403 SCHEDULE-005, 구성원이 아니면 403 SCHEDULE-004이고 발급하지 않는다")
        void rejectsNonManagers() {
            assertRejected(
                    () -> service(null).issue(command("image/jpeg", 1L)), ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
            fixture.givenTechnician(TECHNICIAN_ID);
            assertRejected(() -> service(null).issue(command("image/jpeg", 1L)), ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }

        @Test
        @DisplayName("형식·크기가 없거나 크기가 1 미만이면 400 SCHEDULE-003이다")
        void rejectsMissingInput() {
            fixture.givenManager();

            assertRejected(() -> service(null).issue(command(" ", 1L)), ScheduleErrorCode.INVALID_WORK_INPUT);
            assertRejected(
                    () -> service(null).issue(command("image/jpeg", null)), ScheduleErrorCode.INVALID_WORK_INPUT);
            assertRejected(() -> service(null).issue(command("image/jpeg", 0L)), ScheduleErrorCode.INVALID_WORK_INPUT);
        }

        @Test
        @DisplayName("media가 형식·크기를 거부하면 각각 400 SCHEDULE-020·SCHEDULE-021이다")
        void mapsUploadRejections() {
            fixture.givenManager();
            fixture.mediaFiles.givenUploadRejected(UploadNotAllowedException.Reason.CONTENT_TYPE_NOT_ALLOWED);
            assertRejected(
                    () -> service(null).issue(command("image/gif", 1L)), ScheduleErrorCode.PHOTO_TYPE_NOT_ALLOWED);
            fixture.mediaFiles.givenUploadRejected(UploadNotAllowedException.Reason.SIZE_NOT_ALLOWED);
            assertRejected(() -> service(null).issue(command("image/jpeg", 1L)), ScheduleErrorCode.PHOTO_TOO_LARGE);
        }

        @Test
        @DisplayName("업로드 주소를 서명하지 못하면 원인을 이은 503 SCHEDULE-023이다")
        void signingFailure() {
            fixture.givenManager();
            fixture.mediaFiles.givenSigningFails();

            assertThatThrownBy(() -> service(null).issue(command("image/jpeg", 1L)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(ScheduleErrorCode.PHOTO_STORAGE_UNAVAILABLE))
                    .hasCauseInstanceOf(MediaStorageUnavailableException.class);
        }

        @Test
        @DisplayName("장당 용량 설정은 1바이트 이상 저장소 한도 이하만 받는다")
        void validatesSetting() {
            assertThatThrownBy(() -> new SitePhotoProperties(0L)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SitePhotoProperties(UploadSpec.STORAGE_MAX_BYTES + 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private IssueSitePhotoUploadCommand command(String contentType, Long size) {
            return new IssueSitePhotoUploadCommand(ACCOUNT_ID, ORGANIZATION_ID.value(), contentType, size);
        }
    }

    @Nested
    @DisplayName("완료보고 사진")
    class Completion {

        private final IssueCompletionPhotoUploadService service =
                new IssueCompletionPhotoUploadService(fixture.actorPort, fixture.workRepository, fixture.mediaFiles);

        @Test
        @DisplayName("담당 기사는 작업중 작업에 완료보고 사진 용도·40MB 한도로 발급받는다")
        void assignedTechnicianIssues() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

            service.issue(command(id.value(), "image/heic", 2_000L));

            assertThat(fixture.mediaFiles.uploadRequests())
                    .containsExactly(new UploadRequest(
                            ORGANIZATION_ID.value(),
                            ACCOUNT_ID,
                            new UploadSpec("WORK_COMPLETION_PHOTO", CompletionReport.MAX_PHOTO_BYTES),
                            "image/heic",
                            2_000L));
            assertThat(CompletionReport.MAX_PHOTO_BYTES).isEqualTo(40L * 1024 * 1024);
        }

        /** 발급 기록은 트랜잭션과 함께 롤백돼 남지 않는다(PhotoUploadApiIntegrationTest). 여기서는 오류와 주소를 돌려주지 않는 것만 본다. */
        @ParameterizedTest
        @EnumSource(
                value = WorkStatus.class,
                names = {"PENDING_ACCEPT", "ACCEPTED", "COMPLETED"})
        @DisplayName("작업중이 아니면 409 SCHEDULE-002이다")
        void requiresInProgress(WorkStatus status) {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(status);

            assertThatThrownBy(() -> service.issue(command(id.value(), "image/jpeg", 1L)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(ScheduleErrorCode.INVALID_WORK_STATE));
        }

        @ParameterizedTest
        @EnumSource(
                value = UploadNotAllowedException.Reason.class,
                names = {"CONTENT_TYPE_NOT_ALLOWED", "SIZE_NOT_ALLOWED"})
        @DisplayName("작업중이 아니어도 형식·크기 거부(400 SCHEDULE-020·021)를 상태 오류(409)보다 먼저 알린다")
        void uploadInputComesBeforeState(UploadNotAllowedException.Reason reason) {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.COMPLETED);
            fixture.mediaFiles.givenUploadRejected(reason);

            assertRejected(
                    () -> service.issue(command(id.value(), "image/gif", 1L)),
                    reason == UploadNotAllowedException.Reason.CONTENT_TYPE_NOT_ALLOWED
                            ? ScheduleErrorCode.PHOTO_TYPE_NOT_ALLOWED
                            : ScheduleErrorCode.PHOTO_TOO_LARGE);
        }

        @Test
        @DisplayName("다른 기사의 작업·없는 작업은 404 SCHEDULE-001, 관리자는 403 SCHEDULE-005이다")
        void rejectsOthers() {
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
            fixture.givenTechnician(OTHER_TECHNICIAN_ID);
            assertRejected(
                    () -> service.issue(command(id.value(), "image/jpeg", 1L)), ScheduleErrorCode.WORK_NOT_FOUND);
            assertRejected(
                    () -> service.issue(command(UNKNOWN_WORK_ID, "image/jpeg", 1L)), ScheduleErrorCode.WORK_NOT_FOUND);

            ScheduleServiceFixture managerFixture = new ScheduleServiceFixture();
            managerFixture.givenManager();
            WorkId managerWork = managerFixture.givenWork(WorkStatus.IN_PROGRESS);
            IssueCompletionPhotoUploadService managerService = new IssueCompletionPhotoUploadService(
                    managerFixture.actorPort, managerFixture.workRepository, managerFixture.mediaFiles);
            assertThatThrownBy(() -> managerService.issue(command(managerWork.value(), "image/jpeg", 1L)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(ScheduleErrorCode.ACTION_NOT_ALLOWED));
        }

        @Test
        @DisplayName("업로드 주소를 서명하지 못하면 503 SCHEDULE-023이다")
        void signingFailure() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
            fixture.mediaFiles.givenSigningFails();

            assertThatThrownBy(() -> service.issue(command(id.value(), "image/jpeg", 1L)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                            .isEqualTo(ScheduleErrorCode.PHOTO_STORAGE_UNAVAILABLE));
        }

        @Test
        @DisplayName("크기가 40MB를 넘으면 media가 거부해 400 SCHEDULE-021이다")
        void mapsTooLarge() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
            fixture.mediaFiles.givenUploadRejected(UploadNotAllowedException.Reason.SIZE_NOT_ALLOWED);

            assertRejected(
                    () -> service.issue(command(id.value(), "image/jpeg", CompletionReport.MAX_PHOTO_BYTES + 1)),
                    ScheduleErrorCode.PHOTO_TOO_LARGE);
        }

        private IssueCompletionPhotoUploadCommand command(long workId, String contentType, Long size) {
            return new IssueCompletionPhotoUploadCommand(
                    ACCOUNT_ID, ORGANIZATION_ID.value(), workId, contentType, size);
        }
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ScheduleErrorCode code) {
        int before = fixture.mediaFiles.uploadRequests().size();
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(code));
        if (code != ScheduleErrorCode.PHOTO_TYPE_NOT_ALLOWED && code != ScheduleErrorCode.PHOTO_TOO_LARGE) {
            assertThat(fixture.mediaFiles.uploadRequests()).hasSize(before);
        }
    }
}
