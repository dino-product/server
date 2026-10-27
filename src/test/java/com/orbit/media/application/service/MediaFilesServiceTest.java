package com.orbit.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.orbit.media.AttachmentConflictException;
import com.orbit.media.MediaStorageUnavailableException;
import com.orbit.media.StageRequest;
import com.orbit.media.StagedFiles;
import com.orbit.media.StagedFiles.FileRejection;
import com.orbit.media.StagedFiles.Reason;
import com.orbit.media.UploadNotAllowedException;
import com.orbit.media.UploadRequest;
import com.orbit.media.UploadSpec;
import com.orbit.media.UploadTicket;
import com.orbit.media.application.service.fake.FakeObjectStorage;
import com.orbit.media.application.service.fake.FakeUploadedFileRepository;
import com.orbit.media.domain.UploadedFile;
import com.orbit.media.domain.UploadedFileStatus;
import com.orbit.media.domain.UploadedFilesExpired;

@DisplayName("파일 업로드·연결")
class MediaFilesServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T01:00:00Z");
    private static final long ORG = 1L;
    private static final long ACCOUNT = 7L;
    private static final String PURPOSE = "WORK_SITE_PHOTO";
    private static final String OWNER = "work:10";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', '\n', 0};

    private final FakeUploadedFileRepository repository = new FakeUploadedFileRepository();
    private final FakeObjectStorage storage = new FakeObjectStorage();
    private final List<Object> events = new ArrayList<>();
    private final MediaFilesService service = new MediaFilesService(
            repository,
            storage,
            new MediaFileProperties(List.of("image/jpeg", "image/png"), 30, Map.of()),
            events::add,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Nested
    @DisplayName("업로드 주소 발급")
    class Issue {

        @Test
        @DisplayName("PENDING 기록을 남기고 incoming 키의 서명 주소·헤더·만료 시각을 돌려준다")
        void issuesPendingUpload() {
            UploadTicket ticket = service.issueUpload(request("image/jpeg", 100, 200));

            UUID id = UUID.fromString(ticket.fileId());
            assertThat(ticket.uploadUrl()).isEqualTo("https://storage.test/incoming/" + id);
            assertThat(ticket.requiredHeaders())
                    .containsEntry("content-type", "image/jpeg")
                    .containsEntry("content-length", "100");
            assertThat(ticket.expiresAt()).isEqualTo(FakeObjectStorage.EXPIRES_AT);
            UploadedFile saved = repository.find(id).orElseThrow();
            assertThat(saved.status()).isEqualTo(UploadedFileStatus.PENDING);
            assertThat(saved.isPendingFor(ORG, ACCOUNT, PURPOSE)).isTrue();
            assertThat(saved.sizeBytes()).isEqualTo(100);
        }

        @Test
        @DisplayName("허용 형식이 아니면 거부하고 기록을 남기지 않는다")
        void rejectsContentType() {
            assertThatThrownBy(() -> service.issueUpload(request("image/webp", 100, 200)))
                    .isInstanceOfSatisfying(UploadNotAllowedException.class, exception -> assertThat(exception.reason())
                            .isEqualTo(UploadNotAllowedException.Reason.CONTENT_TYPE_NOT_ALLOWED));
            assertThatThrownBy(() -> service.issueUpload(request("application/pdf", 100, 200)))
                    .isInstanceOf(UploadNotAllowedException.class);
            assertThat(repository.all()).isEmpty();
        }

        @Test
        @DisplayName("주소를 서명하지 못하면(자격 증명을 얻지 못함) 저장소 장애 예외이고 기록을 남기지 않는다")
        void reportsSigningFailure() {
            storage.signingFails(true);

            assertThatThrownBy(() -> service.issueUpload(request("image/jpeg", 100, 200)))
                    .isInstanceOf(MediaStorageUnavailableException.class);
            assertThat(repository.all()).isEmpty();
        }

        @Test
        @DisplayName("크기가 1바이트 미만이거나 최대 크기를 넘으면 거부한다(최대 크기는 받는다)")
        void rejectsSize() {
            assertThatThrownBy(() -> service.issueUpload(request("image/jpeg", 201, 200)))
                    .isInstanceOfSatisfying(UploadNotAllowedException.class, exception -> assertThat(exception.reason())
                            .isEqualTo(UploadNotAllowedException.Reason.SIZE_NOT_ALLOWED));
            assertThatThrownBy(() -> service.issueUpload(request("image/jpeg", 0, 200)))
                    .isInstanceOf(UploadNotAllowedException.class);
            assertThat(service.issueUpload(request("image/jpeg", 200, 200))).isNotNull();
        }
    }

    @Nested
    @DisplayName("연결 전 확인")
    class Stage {

        @Test
        @DisplayName("올라온 이미지를 확인해 media 키로 복사하고, 기록은 아직 PENDING이다")
        void copiesUploadedImage() {
            String fileId = uploaded("image/jpeg", JPEG);

            StagedFiles staged = service.stage(stageRequest(List.of(fileId), null));

            assertThat(staged.findRejection()).isEmpty();
            assertThat(staged.copied()).containsExactly(fileId);
            assertThat(storage.has("media/1/" + fileId)).isTrue();
            assertThat(repository.find(UUID.fromString(fileId)).orElseThrow().status())
                    .isEqualTo(UploadedFileStatus.PENDING);
        }

        @Test
        @DisplayName("저장된 형식 표기가 달라도(대소문자·매개변수) 복사한 객체는 발급 때 기록한 형식으로 둔다")
        void copiesWithRecordedContentType() {
            String fileId = issued("image/jpeg", JPEG.length);
            storage.put("incoming/" + fileId, "IMAGE/JPEG; charset=binary", JPEG);

            StagedFiles staged = service.stage(stageRequest(List.of(fileId), null));

            assertThat(staged.copied()).containsExactly(fileId);
            assertThat(storage.contentType("media/1/" + fileId)).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("빈 목록은 저장소를 부르지 않는다")
        void emptyListDoesNothing() {
            StagedFiles staged = service.stage(stageRequest(List.of(), null));

            assertThat(staged.findRejection()).isEmpty();
            assertThat(storage.calls()).isEmpty();
        }

        @Test
        @DisplayName("발급 기록이 없거나 다른 계정·발주사·용도면 저장소를 부르기 전에 거부한다")
        void rejectsForeignFilesBeforeStorage() {
            String fileId = uploaded("image/jpeg", JPEG);
            int callsBefore = storage.calls().size();

            assertRejected(new StageRequest(ORG, 8L, PURPOSE, List.of(fileId), null), fileId, Reason.NOT_UPLOADED);
            assertRejected(new StageRequest(2L, ACCOUNT, PURPOSE, List.of(fileId), null), fileId, Reason.NOT_UPLOADED);
            assertRejected(
                    new StageRequest(ORG, ACCOUNT, "WORK_COMPLETION_PHOTO", List.of(fileId), null),
                    fileId,
                    Reason.NOT_UPLOADED);
            String unknown = UUID.randomUUID().toString();
            assertRejected(stageRequest(List.of(unknown), null), unknown, Reason.NOT_UPLOADED);
            assertRejected(stageRequest(List.of("photo-1"), null), "photo-1", Reason.NOT_UPLOADED);
            assertThat(storage.calls()).hasSize(callsBefore);
        }

        @Test
        @DisplayName("같은 식별자가 두 번 있으면 중복으로 거부한다")
        void rejectsDuplicates() {
            String fileId = uploaded("image/jpeg", JPEG);

            assertRejected(stageRequest(List.of(fileId, fileId), null), fileId, Reason.DUPLICATED);
        }

        @Test
        @DisplayName("아직 올라오지 않았으면 업로드 안 됨, 형식·시그니처가 다르면 형식 불일치, 더 크면 용량 초과다")
        void verifiesUploadedObject() {
            String notUploaded = issued("image/jpeg", JPEG.length);
            assertRejected(stageRequest(List.of(notUploaded), null), notUploaded, Reason.NOT_UPLOADED);

            String pdf = issued("image/jpeg", PDF.length);
            storage.put("incoming/" + pdf, "image/jpeg", PDF);
            assertRejected(stageRequest(List.of(pdf), null), pdf, Reason.CONTENT_TYPE_MISMATCH);

            String otherType = issued("image/jpeg", JPEG.length);
            storage.put("incoming/" + otherType, "image/png", JPEG);
            assertRejected(stageRequest(List.of(otherType), null), otherType, Reason.CONTENT_TYPE_MISMATCH);

            String larger = issued("image/jpeg", JPEG.length - 1);
            storage.put("incoming/" + larger, "image/jpeg", JPEG);
            assertRejected(stageRequest(List.of(larger), null), larger, Reason.TOO_LARGE);
            assertThat(storage.calls()).noneMatch(call -> call.startsWith("copy"));
        }

        @Test
        @DisplayName("메타데이터를 확인한 뒤 같은 키에 다시 올렸거나 지웠으면 앞부분을 읽지 못해 업로드 안 됨이고 복사하지 않는다")
        void objectChangedOrRemovedBeforeRead() {
            String replaced = uploaded("image/jpeg", JPEG);
            storage.afterCall(
                    "find incoming/" + replaced, () -> storage.put("incoming/" + replaced, "image/jpeg", PDF.clone()));
            assertRejected(stageRequest(List.of(replaced), null), replaced, Reason.NOT_UPLOADED);

            String removed = uploaded("image/jpeg", JPEG);
            storage.afterCall("find incoming/" + removed, () -> storage.remove("incoming/" + removed));
            assertRejected(stageRequest(List.of(removed), null), removed, Reason.NOT_UPLOADED);

            assertThat(storage.calls()).noneMatch(call -> call.startsWith("copy"));
        }

        @Test
        @DisplayName("앞부분을 확인한 뒤 다시 올렸거나 지웠으면 복사하지 못해 업로드 안 됨이고 연결 위치에 객체가 없다")
        void objectChangedOrRemovedBeforeCopy() {
            String replaced = uploaded("image/jpeg", JPEG);
            storage.afterCall(
                    "read incoming/" + replaced, () -> storage.put("incoming/" + replaced, "image/jpeg", PDF.clone()));
            assertRejected(stageRequest(List.of(replaced), null), replaced, Reason.NOT_UPLOADED);
            assertThat(storage.has("media/1/" + replaced)).isFalse();

            String removed = uploaded("image/jpeg", JPEG);
            storage.afterCall("read incoming/" + removed, () -> storage.remove("incoming/" + removed));
            assertRejected(stageRequest(List.of(removed), null), removed, Reason.NOT_UPLOADED);
            assertThat(storage.has("media/1/" + removed)).isFalse();
        }

        @Test
        @DisplayName("이미 같은 대상에 연결된 파일은 업로더와 관계없이 저장소를 다시 부르지 않고 둔다")
        void keepsFilesAttachedToOwner() {
            String fileId = attached(OWNER);
            int callsBefore = storage.calls().size();

            StagedFiles staged = service.stage(new StageRequest(ORG, 99L, PURPOSE, List.of(fileId), OWNER));

            assertThat(staged.findRejection()).isEmpty();
            assertThat(staged.copied()).isEmpty();
            assertThat(storage.calls()).hasSize(callsBefore);
            assertRejected(
                    new StageRequest(ORG, 99L, PURPOSE, List.of(fileId), "work:11"), fileId, Reason.NOT_UPLOADED);
            assertRejected(stageRequest(List.of(fileId), null), fileId, Reason.NOT_UPLOADED);
        }

        @Test
        @DisplayName("저장소에 닿지 못하면 저장소 장애 예외다")
        void reportsStorageFailure() {
            String fileId = uploaded("image/jpeg", JPEG);
            storage.failing(true);

            assertThatThrownBy(() -> service.stage(stageRequest(List.of(fileId), null)))
                    .isInstanceOf(MediaStorageUnavailableException.class);
        }

        private void assertRejected(StageRequest request, String fileId, Reason reason) {
            StagedFiles staged = service.stage(request);
            assertThat(staged.findRejection()).contains(new FileRejection(fileId, reason));
            assertThat(staged.copied()).isEmpty();
        }
    }

    @Nested
    @DisplayName("연결 확정")
    class Attach {

        @Test
        @DisplayName("복사한 파일을 대상에 연결하고 연결 시각을 남긴다")
        void attachesStagedFiles() {
            String first = uploaded("image/jpeg", JPEG);
            String second = uploaded("image/jpeg", JPEG);
            StagedFiles staged = service.stage(stageRequest(List.of(first, second), null));

            service.attach(staged, OWNER);

            for (String fileId : List.of(first, second)) {
                UploadedFile file = repository.find(UUID.fromString(fileId)).orElseThrow();
                assertThat(file.isAttachedTo(ORG, PURPOSE, OWNER)).isTrue();
                assertThat(file.attachedAt()).contains(NOW);
            }
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("같은 요청을 다시 연결해도 바뀌지 않는다(재시도)")
        void reattachingSameFilesIsNoop() {
            String fileId = uploaded("image/jpeg", JPEG);
            StagedFiles staged = service.stage(stageRequest(List.of(fileId), null));
            service.attach(staged, OWNER);

            service.attach(staged, OWNER);
            service.attach(service.stage(stageRequest(List.of(fileId), OWNER)), OWNER);

            assertThat(repository.find(UUID.fromString(fileId)).orElseThrow().isAttachedTo(ORG, PURPOSE, OWNER))
                    .isTrue();
            assertThat(events).isEmpty();
        }

        @Test
        @DisplayName("목록에서 빠진 연결 파일은 만료로 바꾸고 커밋 뒤 표시할 객체 키를 발행한다")
        void expiresFilesRemovedFromOwner() {
            List<String> before = attached(OWNER, 2);
            String kept = before.get(0);
            String removed = before.get(1);
            String otherOwner = attached("work:11");
            String added = uploaded("image/jpeg", JPEG);

            service.attach(service.stage(stageRequest(List.of(kept, added), OWNER)), OWNER);

            assertThat(repository.find(UUID.fromString(removed)).orElseThrow().status())
                    .isEqualTo(UploadedFileStatus.EXPIRED);
            assertThat(repository.find(UUID.fromString(kept)).orElseThrow().isAttachedTo(ORG, PURPOSE, OWNER))
                    .isTrue();
            assertThat(repository.find(UUID.fromString(added)).orElseThrow().isAttachedTo(ORG, PURPOSE, OWNER))
                    .isTrue();
            assertThat(repository
                            .find(UUID.fromString(otherOwner))
                            .orElseThrow()
                            .status())
                    .isEqualTo(UploadedFileStatus.ATTACHED);
            assertThat(events).containsExactly(new UploadedFilesExpired(List.of("media/1/" + removed)));
            assertThat(storage.expired()).isEmpty();
        }

        @Test
        @DisplayName("빈 목록으로 확정하면 그 대상의 연결 파일을 모두 만료한다")
        void emptyListExpiresAll() {
            String removed = attached(OWNER);

            service.attach(service.stage(stageRequest(List.of(), OWNER)), OWNER);

            assertThat(repository.find(UUID.fromString(removed)).orElseThrow().status())
                    .isEqualTo(UploadedFileStatus.EXPIRED);
        }

        @Test
        @DisplayName("확인 뒤 다른 요청이 먼저 연결했으면 충돌이다")
        void conflictsWhenFileChangedAfterStaging() {
            String fileId = uploaded("image/jpeg", JPEG);
            StagedFiles staged = service.stage(stageRequest(List.of(fileId), null));
            service.attach(staged, "work:11");

            assertThatThrownBy(() -> service.attach(staged, OWNER)).isInstanceOf(AttachmentConflictException.class);
        }

        @Test
        @DisplayName("거부가 있는 확인 결과는 연결하지 않는다")
        void refusesRejectedResult() {
            StagedFiles rejected = service.stage(stageRequest(List.of("photo-1"), null));

            assertThatThrownBy(() -> service.attach(rejected, OWNER)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("내려받기 주소는 media 키를 서명한다")
    void signsDownloadUrl() {
        assertThat(service.downloadUrl(ORG, "abc")).isEqualTo("https://storage.test/get/media/1/abc");
    }

    @Test
    @DisplayName("내려받기 주소를 서명하지 못하면 저장소 장애 예외다")
    void reportsDownloadSigningFailure() {
        storage.signingFails(true);

        assertThatThrownBy(() -> service.downloadUrl(ORG, "abc")).isInstanceOf(MediaStorageUnavailableException.class);
    }

    private UploadRequest request(String contentType, long size, long maxBytes) {
        return new UploadRequest(ORG, ACCOUNT, new UploadSpec(PURPOSE, maxBytes), contentType, size);
    }

    private StageRequest stageRequest(List<String> fileIds, String owner) {
        return new StageRequest(ORG, ACCOUNT, PURPOSE, fileIds, owner);
    }

    private String issued(String contentType, long size) {
        return service.issueUpload(request(contentType, size, 1_000)).fileId();
    }

    private String uploaded(String contentType, byte[] bytes) {
        String fileId = issued(contentType, bytes.length);
        storage.put("incoming/" + fileId, contentType, bytes);
        return fileId;
    }

    private String attached(String owner) {
        return attached(owner, 1).getFirst();
    }

    /** 새 대상에 파일 {@code count}개를 한 번에 연결한다(연결은 목록을 그 대상의 파일 전체로 만든다). */
    private List<String> attached(String owner, int count) {
        List<String> fileIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            fileIds.add(uploaded("image/jpeg", JPEG));
        }
        service.attach(service.stage(stageRequest(fileIds, null)), owner);
        return fileIds;
    }
}
