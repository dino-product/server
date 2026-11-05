package com.orbit.media.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.orbit.media.application.port.in.command.dto.ExpireStoredObjectsCommand;
import com.orbit.media.application.service.fake.FakeObjectStorage;

@DisplayName("만료 표시")
@ExtendWith(OutputCaptureExtension.class)
class ExpireStoredObjectsServiceTest {

    private final FakeObjectStorage storage = new FakeObjectStorage();
    private final ExpireStoredObjectsService service = new ExpireStoredObjectsService(storage);

    @Test
    @DisplayName("받은 객체 키마다 만료 표시를 남긴다")
    void marksEveryKey() {
        service.expire(new ExpireStoredObjectsCommand(List.of("media/1/a", "media/1/b")));

        assertThat(storage.expired()).containsExactlyInAnyOrder("media/1/a", "media/1/b");
    }

    @Test
    @DisplayName("한 객체의 표시가 저장소 장애로 실패해도 예외를 던지지 않고 경고를 남긴 뒤 다음 객체를 표시한다")
    void continuesAfterFailure(CapturedOutput output) {
        storage.failingFor("media/1/b");

        assertThatCode(() ->
                        service.expire(new ExpireStoredObjectsCommand(List.of("media/1/a", "media/1/b", "media/1/c"))))
                .doesNotThrowAnyException();

        assertThat(storage.expired()).containsExactlyInAnyOrder("media/1/a", "media/1/c");
        assertThat(storage.calls())
                .containsExactly("markExpired media/1/a", "markExpired media/1/b", "markExpired media/1/c");
        assertThat(output).contains("failed to mark media object as expired: media/1/b");
    }
}
