package com.orbit.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("업로드 파일")
class UploadedFileTest {

    private static final UUID ID = UUID.fromString("0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a6b");
    private static final Instant NOW = Instant.parse("2026-10-21T01:00:00Z");

    @Test
    @DisplayName("발급한 파일은 PENDING이고 incoming 키에 있으며 Content-Type은 정규형으로 둔다")
    void issuesPendingFile() {
        UploadedFile file = UploadedFile.issue(ID, 1L, 7L, "WORK_SITE_PHOTO", "Image/JPEG", 100);

        assertThat(file.status()).isEqualTo(UploadedFileStatus.PENDING);
        assertThat(file.objectKey()).isEqualTo("incoming/" + ID);
        assertThat(file.mediaKey()).isEqualTo("media/1/" + ID);
        assertThat(file.contentType()).isEqualTo("image/jpeg");
        assertThat(file.isPendingFor(1L, 7L, "WORK_SITE_PHOTO")).isTrue();
        assertThat(file.isPendingFor(1L, 8L, "WORK_SITE_PHOTO")).isFalse();
        assertThat(file.isPendingFor(2L, 7L, "WORK_SITE_PHOTO")).isFalse();
        assertThat(file.isPendingFor(1L, 7L, "WORK_COMPLETION_PHOTO")).isFalse();
    }

    @Test
    @DisplayName("연결하면 대상과 시각을 남기고 media 키로 옮겨지며, 만료하면 기록은 남긴 채 대상에서 빠진다")
    void attachesAndExpires() {
        UploadedFile file = UploadedFile.issue(ID, 1L, 7L, "WORK_SITE_PHOTO", "image/png", 100);

        file.attach("work:10", NOW);

        assertThat(file.status()).isEqualTo(UploadedFileStatus.ATTACHED);
        assertThat(file.objectKey()).isEqualTo("media/1/" + ID);
        assertThat(file.isAttachedTo(1L, "WORK_SITE_PHOTO", "work:10")).isTrue();
        assertThat(file.isAttachedTo(1L, "WORK_SITE_PHOTO", "work:11")).isFalse();
        assertThat(file.isPendingFor(1L, 7L, "WORK_SITE_PHOTO")).isFalse();

        file.expire();

        assertThat(file.status()).isEqualTo(UploadedFileStatus.EXPIRED);
        assertThat(file.owner()).contains("work:10");
        assertThat(file.attachedAt()).contains(NOW);
        assertThat(file.isAttachedTo(1L, "WORK_SITE_PHOTO", "work:10")).isFalse();
    }

    @Test
    @DisplayName("PENDING이 아니면 연결할 수 없고 연결되지 않았으면 만료할 수 없다")
    void rejectsInvalidTransitions() {
        UploadedFile file = UploadedFile.issue(ID, 1L, 7L, "WORK_SITE_PHOTO", "image/png", 100);
        assertThatThrownBy(file::expire).isInstanceOf(IllegalStateException.class);

        file.attach("work:10", NOW);
        assertThatThrownBy(() -> file.attach("work:11", NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> UploadedFile.issue(ID, 1L, 7L, "WORK_SITE_PHOTO", "image/png", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장값 복원은 연결 기록과 상태가 맞아야 한다")
    void reconstitutionRequiresConsistentAttachment() {
        assertThatThrownBy(() -> UploadedFile.reconstitute(
                        ID, 1L, 7L, "P", "image/png", 1, UploadedFileStatus.ATTACHED, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UploadedFile.reconstitute(
                        ID, 1L, 7L, "P", "image/png", 1, UploadedFileStatus.PENDING, "work:1", NOW, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(UploadedFile.reconstitute(
                                ID, 1L, 7L, "P", "image/png", 1, UploadedFileStatus.EXPIRED, "work:1", NOW, null)
                        .status())
                .isEqualTo(UploadedFileStatus.EXPIRED);
    }
}
