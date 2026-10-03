package com.orbit.schedule.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("임시 사진 주소 어댑터")
class FakePhotoUrlAdapterTest {

    @Test
    @DisplayName("사진 식별자를 붙인 가짜 주소를 돌려준다")
    void returnsFakeUrl() {
        assertThat(new FakePhotoUrlAdapter().urlOf("photo-1")).isEqualTo("fake-photo://photo-1");
    }
}
