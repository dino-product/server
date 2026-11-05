package com.orbit.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("파일 정책 설정")
class MediaFilePropertiesTest {

    @Test
    @DisplayName("용도별 보관 기간이 보관 상한을 넘으면 기동에 실패한다")
    void rejectsRetentionAboveMax() {
        assertThatThrownBy(() -> new MediaFileProperties(List.of("image/jpeg"), 30, Map.of("WORK_SITE_PHOTO", 31)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MEDIA_RETENTION_MAX_DAYS");
        assertThat(new MediaFileProperties(List.of("image/jpeg"), 30, Map.of("WORK_SITE_PHOTO", 30)).retentionDays())
                .containsEntry("WORK_SITE_PHOTO", 30);
    }

    @Test
    @DisplayName("보관 상한이 없거나 1일 미만이면 기동에 실패한다")
    void requiresRetentionMax() {
        assertThatThrownBy(() -> new MediaFileProperties(List.of("image/jpeg"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaFileProperties(List.of("image/jpeg"), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("시그니처로 확인할 수 없는 형식은 허용 형식에 둘 수 없고, 형식은 정규형으로 비교한다")
    void allowsOnlyVerifiableImageTypes() {
        assertThatThrownBy(() -> new MediaFileProperties(List.of("image/gif"), 30, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MediaFileProperties(List.of(), 30, null))
                .isInstanceOf(IllegalArgumentException.class);

        MediaFileProperties properties = new MediaFileProperties(List.of("Image/HEIC"), 30, null);
        assertThat(properties.allows("image/heic")).isTrue();
        assertThat(properties.allows("image/heif")).isFalse();
        assertThat(properties.allows(null)).isFalse();
    }
}
