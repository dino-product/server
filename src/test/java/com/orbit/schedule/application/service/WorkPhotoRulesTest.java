package com.orbit.schedule.application.service;

import static com.orbit.schedule.application.service.ScheduleServiceFixture.ACCOUNT_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.ORGANIZATION_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TECHNICIAN_ID;
import static com.orbit.schedule.application.service.ScheduleServiceFixture.TEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.orbit.media.StageRequest;
import com.orbit.media.StagedFiles.Reason;
import com.orbit.schedule.application.error.ScheduleErrorCode;
import com.orbit.schedule.application.port.in.command.dto.CreateWorkCommand;
import com.orbit.schedule.application.port.in.command.dto.SubmitCompletionReportCommand;
import com.orbit.schedule.application.port.in.command.dto.UpdateWorkDetailsCommand;
import com.orbit.schedule.application.service.fake.FakeMediaFiles.Attachment;
import com.orbit.schedule.domain.ActorRole;
import com.orbit.schedule.domain.CompletionReport;
import com.orbit.schedule.domain.Work;
import com.orbit.schedule.domain.WorkId;
import com.orbit.schedule.domain.WorkStatus;

/** 사진을 받는 유즈케이스(등록·기본정보 수정·완료보고 제출)가 media로 사진을 확인·연결하는 규칙. */
@DisplayName("작업 사진 확인·연결")
class WorkPhotoRulesTest {

    private static final String PHOTO_1 = "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a61";
    private static final String PHOTO_2 = "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a62";

    private final ScheduleServiceFixture fixture = new ScheduleServiceFixture();

    static Stream<Arguments> rejections() {
        return Stream.of(
                Arguments.of(Reason.NOT_UPLOADED, ScheduleErrorCode.PHOTO_NOT_UPLOADED),
                Arguments.of(Reason.CONTENT_TYPE_MISMATCH, ScheduleErrorCode.PHOTO_TYPE_NOT_ALLOWED),
                Arguments.of(Reason.TOO_LARGE, ScheduleErrorCode.PHOTO_TOO_LARGE),
                Arguments.of(Reason.DUPLICATED, ScheduleErrorCode.INVALID_WORK_INPUT));
    }

    @Nested
    @DisplayName("작업 등록")
    class Create {

        @Test
        @DisplayName("현장 사진을 요청자·조직·현장 사진 용도로 확인하고, 저장한 작업에 연결한다")
        void stagesAndAttachesToSavedWork() {
            fixture.givenManager();

            fixture.createWorkService().create(create(List.of(PHOTO_1, PHOTO_2), false));

            assertThat(fixture.mediaFiles.stageRequests())
                    .containsExactly(new StageRequest(
                            ORGANIZATION_ID.value(), ACCOUNT_ID, "WORK_SITE_PHOTO", List.of(PHOTO_1, PHOTO_2), null));
            Work saved = fixture.singleSaved();
            assertThat(saved.sitePhotos()).containsExactly(PHOTO_1, PHOTO_2);
            assertThat(fixture.mediaFiles.attachments())
                    .extracting(Attachment::owner)
                    .containsExactly("work:" + saved.id().orElseThrow().value());
        }

        @Test
        @DisplayName("대문자로 보낸 사진 식별자는 소문자로 바꿔 확인하고 저장한다")
        void normalizesUppercaseIds() {
            fixture.givenManager();

            fixture.createWorkService().create(create(List.of(PHOTO_1.toUpperCase(Locale.ROOT)), false));

            assertThat(fixture.mediaFiles.stageRequests())
                    .singleElement()
                    .satisfies(request -> assertThat(request.fileIds()).containsExactly(PHOTO_1));
            assertThat(fixture.singleSaved().sitePhotos()).containsExactly(PHOTO_1);
        }

        @Test
        @DisplayName("사진 없이 등록하면 저장소를 부르지 않고 빈 목록으로 연결한다")
        void withoutPhotosDoesNotCallStorage() {
            fixture.givenManager();

            fixture.createWorkService().create(create(null, false));

            assertThat(fixture.mediaFiles.stageRequests()).isEmpty();
            assertThat(fixture.mediaFiles.attachments()).singleElement().satisfies(attachment -> assertThat(
                            attachment.staged().fileIds())
                    .isEmpty());
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.application.service.WorkPhotoRulesTest#rejections")
        @DisplayName("확인에 실패한 사진이 있으면 사유의 오류로 거부하고 저장·연결하지 않는다")
        void rejectsByReason(Reason reason, ScheduleErrorCode expected) {
            fixture.givenManager();
            fixture.mediaFiles.givenRejected(PHOTO_2, reason);

            fixture.assertRejected(
                    () -> fixture.createWorkService().create(create(List.of(PHOTO_1, PHOTO_2), false)), expected);
            assertThat(fixture.mediaFiles.attachments()).isEmpty();
        }

        @Test
        @DisplayName("요청자가 조직 구성원이 아니면 사진 확인 결과보다 403이 먼저다")
        void requesterCheckComesBeforePhotos() {
            fixture.mediaFiles.givenRejected(PHOTO_1, Reason.NOT_UPLOADED);

            fixture.assertRejected(
                    () -> fixture.createWorkService().create(create(List.of(PHOTO_1), false)),
                    ScheduleErrorCode.NOT_ORGANIZATION_MEMBER);
        }

        @Test
        @DisplayName("사진이 7장이면 저장소를 부르지 않고 입력 오류로 거부한다")
        void tooManyPhotosSkipStorage() {
            fixture.givenManager();
            fixture.mediaFiles.givenRejected(PHOTO_1, Reason.NOT_UPLOADED);

            fixture.assertRejected(
                    () -> fixture.createWorkService().create(create(seven(), false)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
            assertThat(fixture.mediaFiles.stageRequests()).isEmpty();
        }

        @Test
        @DisplayName("저장소에 닿지 못하면 503 SCHEDULE-023이고 작업을 만들지 않는다")
        void storageDown() {
            fixture.givenManager();
            fixture.mediaFiles.givenStorageDown();

            fixture.assertRejected(
                    () -> fixture.createWorkService().create(create(List.of(PHOTO_1), false)),
                    ScheduleErrorCode.PHOTO_STORAGE_UNAVAILABLE);
        }

        @Test
        @DisplayName("확인 뒤 다른 요청이 사진을 먼저 썼으면 400 SCHEDULE-022다(트랜잭션은 롤백)")
        void attachConflict() {
            fixture.givenManager();
            fixture.mediaFiles.givenAttachConflict();

            assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                                    com.orbit.shared.error.BusinessException.class,
                                    () -> fixture.createWorkService().create(create(List.of(PHOTO_1), false)))
                            .getErrorCode())
                    .isEqualTo(ScheduleErrorCode.PHOTO_NOT_UPLOADED);
        }

        @Test
        @DisplayName("겹침을 확인하지 않아 작업을 만들지 않으면 사진도 연결하지 않는다")
        void unconfirmedConflictDoesNotAttach() {
            fixture.givenManager();
            fixture.givenWork(WorkStatus.PENDING_ACCEPT);
            int savedBefore = fixture.workRepository.saved().size();

            fixture.createWorkService().create(create(List.of(PHOTO_1), true));

            assertThat(fixture.workRepository.saved()).hasSize(savedBefore);
            assertThat(fixture.mediaFiles.attachments()).isEmpty();
        }
    }

    @Nested
    @DisplayName("기본정보 수정")
    class Update {

        @Test
        @DisplayName("이미 그 작업에 붙은 사진도 받도록 작업을 연결 대상으로 확인하고, 보낸 목록을 그 작업의 현장 사진 전체로 연결한다")
        void stagesWithOwnerAndAttaches() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

            fixture.updateWorkDetailsService().update(update(id.value(), List.of(PHOTO_2)));

            assertThat(fixture.mediaFiles.stageRequests())
                    .containsExactly(new StageRequest(
                            ORGANIZATION_ID.value(),
                            ACCOUNT_ID,
                            "WORK_SITE_PHOTO",
                            List.of(PHOTO_2),
                            "work:" + id.value()));
            assertThat(fixture.stored(id).sitePhotos()).containsExactly(PHOTO_2);
            assertThat(fixture.mediaFiles.attachments()).singleElement().satisfies(attachment -> {
                assertThat(attachment.owner()).isEqualTo("work:" + id.value());
                assertThat(attachment.staged().fileIds()).containsExactly(PHOTO_2);
            });
        }

        @Test
        @DisplayName("대문자로 보낸 사진 식별자는 소문자로 바꿔 확인하고 저장한다")
        void normalizesUppercaseIds() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

            fixture.updateWorkDetailsService().update(update(id.value(), List.of(PHOTO_2.toUpperCase(Locale.ROOT))));

            assertThat(fixture.mediaFiles.stageRequests())
                    .singleElement()
                    .satisfies(request -> assertThat(request.fileIds()).containsExactly(PHOTO_2));
            assertThat(fixture.stored(id).sitePhotos()).containsExactly(PHOTO_2);
        }

        @Test
        @DisplayName("사진을 모두 빼면 빈 목록으로 연결해 그 작업의 사진을 만료시킨다")
        void clearingPhotosAttachesEmptyList() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

            fixture.updateWorkDetailsService().update(update(id.value(), null));

            assertThat(fixture.mediaFiles.attachments()).singleElement().satisfies(attachment -> assertThat(
                            attachment.staged().fileIds())
                    .isEmpty());
        }

        @Test
        @DisplayName("등록자도 총관리자도 아닌 직원이면 사진 확인 결과보다 403이 먼저다")
        void permissionComesBeforePhotos() {
            fixture.givenManager(ActorRole.STAFF);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);
            fixture.mediaFiles.givenRejected(PHOTO_1, Reason.NOT_UPLOADED);

            fixture.assertRejected(
                    () -> fixture.updateWorkDetailsService().update(update(id.value(), List.of(PHOTO_1))),
                    ScheduleErrorCode.ACTION_NOT_ALLOWED);
        }

        @Test
        @DisplayName("사진이 7장이면 저장소를 부르지 않고 입력 오류로 거부한다")
        void tooManyPhotosSkipStorage() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);

            fixture.assertRejected(
                    () -> fixture.updateWorkDetailsService().update(update(id.value(), seven())),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
            assertThat(fixture.mediaFiles.stageRequests()).isEmpty();
        }

        @Test
        @DisplayName("완료된 작업이어도 사진 확인 결과(400 SCHEDULE-022)를 상태 오류(409)보다 먼저 알린다")
        void photoResultComesBeforeState() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.COMPLETED);
            fixture.mediaFiles.givenRejected(PHOTO_1, Reason.NOT_UPLOADED);

            fixture.assertRejected(
                    () -> fixture.updateWorkDetailsService().update(update(id.value(), List.of(PHOTO_1))),
                    ScheduleErrorCode.PHOTO_NOT_UPLOADED);
        }

        @Test
        @DisplayName("작업명이 틀리면 사진 확인 결과보다 입력 오류(400 SCHEDULE-003)가 먼저다")
        void domainInputComesBeforePhotoResult() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);
            fixture.mediaFiles.givenRejected(PHOTO_1, Reason.NOT_UPLOADED);
            UpdateWorkDetailsCommand blankName = new UpdateWorkDetailsCommand(
                    ACCOUNT_ID,
                    ORGANIZATION_ID.value(),
                    id.value(),
                    " ",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(PHOTO_1));

            fixture.assertRejected(
                    () -> fixture.updateWorkDetailsService().update(blankName), ScheduleErrorCode.INVALID_WORK_INPUT);
        }

        @Test
        @DisplayName("올리지 않은 사진이면 400 SCHEDULE-022이고 바꾸지 않는다")
        void rejectsNotUploaded() {
            fixture.givenManager(ActorRole.OWNER);
            WorkId id = fixture.givenWork(WorkStatus.REGISTERED);
            fixture.mediaFiles.givenRejected(PHOTO_1, Reason.NOT_UPLOADED);

            fixture.assertRejected(
                    () -> fixture.updateWorkDetailsService().update(update(id.value(), List.of(PHOTO_1))),
                    ScheduleErrorCode.PHOTO_NOT_UPLOADED);
            assertThat(fixture.stored(id).name()).isEqualTo("대상 작업");
        }
    }

    @Nested
    @DisplayName("완료보고 제출")
    class Submit {

        @Test
        @DisplayName("작업 전·후 사진을 함께 완료보고 사진 용도로 확인하고, 완료한 작업에 연결한다")
        void stagesBothListsAndAttaches() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

            fixture.submitCompletionReportService().submit(submit(id.value(), List.of(PHOTO_1), List.of(PHOTO_2)));

            assertThat(fixture.mediaFiles.stageRequests())
                    .containsExactly(new StageRequest(
                            ORGANIZATION_ID.value(),
                            ACCOUNT_ID,
                            "WORK_COMPLETION_PHOTO",
                            List.of(PHOTO_1, PHOTO_2),
                            "work:" + id.value()));
            assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.COMPLETED);
            assertThat(fixture.mediaFiles.attachments())
                    .extracting(Attachment::owner)
                    .containsExactly("work:" + id.value());
        }

        @Test
        @DisplayName("대문자로 보낸 작업 전·후 사진 식별자는 소문자로 바꿔 확인하고 저장한다")
        void normalizesUppercaseIds() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

            fixture.submitCompletionReportService()
                    .submit(submit(
                            id.value(),
                            List.of(PHOTO_1.toUpperCase(Locale.ROOT)),
                            List.of(PHOTO_2.toUpperCase(Locale.ROOT))));

            assertThat(fixture.mediaFiles.stageRequests())
                    .singleElement()
                    .satisfies(request -> assertThat(request.fileIds()).containsExactly(PHOTO_1, PHOTO_2));
            CompletionReport report = fixture.stored(id).completionReport().orElseThrow();
            assertThat(report.beforePhotos()).containsExactly(PHOTO_1);
            assertThat(report.afterPhotos()).containsExactly(PHOTO_2);
        }

        @Test
        @DisplayName("같은 보고를 다시 보내면 성공하고 다시 연결하지 않는다")
        void duplicateSubmitDoesNotAttachAgain() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
            SubmitCompletionReportCommand command = submit(id.value(), List.of(PHOTO_1), List.of(PHOTO_2));
            fixture.submitCompletionReportService().submit(command);

            fixture.submitCompletionReportService().submit(command);

            assertThat(fixture.mediaFiles.attachments()).hasSize(1);
        }

        @ParameterizedTest
        @MethodSource("com.orbit.schedule.application.service.WorkPhotoRulesTest#rejections")
        @DisplayName("확인에 실패한 사진이 있으면 사유의 오류로 거부하고 완료하지 않는다")
        void rejectsByReason(Reason reason, ScheduleErrorCode expected) {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);
            fixture.mediaFiles.givenRejected(PHOTO_2, reason);

            fixture.assertRejected(
                    () -> fixture.submitCompletionReportService()
                            .submit(submit(id.value(), List.of(PHOTO_1), List.of(PHOTO_2))),
                    expected);
            assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("작업 전이나 작업 후 사진이 7장이면 저장소를 부르지 않고 입력 오류로 거부한다(합쳐 12장 이하여도)")
        void tooManyPhotosOnEitherSideSkipStorage() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

            fixture.assertRejected(
                    () -> fixture.submitCompletionReportService().submit(submit(id.value(), seven(), null)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
            fixture.assertRejected(
                    () -> fixture.submitCompletionReportService().submit(submit(id.value(), List.of(PHOTO_1), seven())),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
            assertThat(fixture.mediaFiles.stageRequests()).isEmpty();
            assertThat(fixture.stored(id).status()).isEqualTo(WorkStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("빈 사진 식별자는 저장소를 부르지 않고 입력 오류로 거부한다")
        void blankPhotoIsInputError() {
            fixture.givenTechnician(TECHNICIAN_ID);
            WorkId id = fixture.givenWork(WorkStatus.IN_PROGRESS);

            fixture.assertRejected(
                    () -> fixture.submitCompletionReportService().submit(submit(id.value(), List.of(" "), null)),
                    ScheduleErrorCode.INVALID_WORK_INPUT);
        }
    }

    /** 장수 상한(6장)을 하나 넘는 사진 목록. */
    private static List<String> seven() {
        return List.of(
                PHOTO_1,
                PHOTO_2,
                "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a63",
                "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a64",
                "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a65",
                "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a66",
                "0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a67");
    }

    private static CreateWorkCommand create(List<String> photos, boolean withSchedule) {
        return new CreateWorkCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                "에어컨 수리",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                photos,
                withSchedule ? TECHNICIAN_ID.value() : null,
                withSchedule ? TEN : null,
                withSchedule ? Duration.ofHours(2) : null,
                false);
    }

    private static UpdateWorkDetailsCommand update(long workId, List<String> photos) {
        return new UpdateWorkDetailsCommand(
                ACCOUNT_ID,
                ORGANIZATION_ID.value(),
                workId,
                "보일러 점검",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                photos);
    }

    private static SubmitCompletionReportCommand submit(long workId, List<String> before, List<String> after) {
        return new SubmitCompletionReportCommand(
                ACCOUNT_ID, ORGANIZATION_ID.value(), workId, 1, before, after, null, null, null, null);
    }
}
