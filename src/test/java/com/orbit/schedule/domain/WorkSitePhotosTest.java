package com.orbit.schedule.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("작업의 현장 사진")
class WorkSitePhotosTest {

    private static final OrganizationId ORGANIZATION = new OrganizationId(100L);
    private static final MembershipId REGISTRAR = new MembershipId(1L);
    private static final Instant REGISTERED_AT = Instant.parse("2026-10-01T01:00:00Z");

    @Test
    @DisplayName("업로드를 마친 사진 식별자를 붙인 순서대로 최대 6장까지 남기고, 비우면 지운다")
    void keepsUpToSixPhotosInOrder() {
        Work work = register();
        List<String> six = List.of("p1", "p2", "p3", "p4", "p5", "p6");

        work.replaceSitePhotos(six);
        assertThat(work.sitePhotos()).containsExactlyElementsOf(six);

        work.replaceSitePhotos(null);
        assertThat(work.sitePhotos()).isEmpty();
    }

    @Test
    @DisplayName("7장 이상이거나 빈·너무 긴 식별자는 받지 않고 기존 사진을 그대로 둔다")
    void rejectsInvalidPhotos() {
        Work work = register();
        work.replaceSitePhotos(List.of("keep"));
        List<String> withNull = new ArrayList<>();
        withNull.add(null);

        for (List<String> invalid : List.of(
                Collections.nCopies(Work.MAX_SITE_PHOTO_COUNT + 1, "p"),
                List.of(" "),
                withNull,
                List.of("x".repeat(Work.MAX_SITE_PHOTO_ID_LENGTH + 1)))) {
            assertThatThrownBy(() -> work.replaceSitePhotos(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(work.sitePhotos()).containsExactly("keep");
        work.replaceSitePhotos(List.of("x".repeat(Work.MAX_SITE_PHOTO_ID_LENGTH)));
    }

    @Test
    @DisplayName("취소해도 붙인 현장 사진은 남고, 끝난 작업의 사진은 바꿀 수 없으며 잘못된 입력은 상태보다 먼저 거부한다")
    void keepsPhotosButRejectsChangeOfTerminalWork() {
        Work work = register();
        work.replaceSitePhotos(List.of("p1"));
        work.cancel(REGISTERED_AT, REGISTRAR, CancellationReason.DUPLICATE_REGISTRATION, null);

        assertThat(work.sitePhotos()).containsExactly("p1");
        assertThatThrownBy(() -> work.replaceSitePhotos(List.of("p2"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> work.replaceSitePhotos(List.of(" "))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("저장값의 현장 사진을 그대로 복원하고 잘못된 저장값은 거부한다")
    void restoresSitePhotos() {
        assertThat(restore(List.of("p1", "p2")).sitePhotos()).containsExactly("p1", "p2");
        assertThat(restore(null).sitePhotos()).isEmpty();
        assertThatThrownBy(() -> restore(Collections.nCopies(Work.MAX_SITE_PHOTO_COUNT + 1, "p")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Work register() {
        return Work.register(ORGANIZATION, "보일러 점검", REGISTRAR, REGISTERED_AT, null, null, null);
    }

    private static Work restore(List<String> sitePhotos) {
        return Work.reconstitute(
                new WorkId(10L),
                ORGANIZATION,
                "보일러 점검",
                REGISTRAR,
                REGISTERED_AT,
                null,
                null,
                null,
                null,
                null,
                WorkStatus.REGISTERED,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                sitePhotos);
    }
}
