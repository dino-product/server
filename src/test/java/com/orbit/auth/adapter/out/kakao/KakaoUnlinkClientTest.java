package com.orbit.auth.adapter.out.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.auth.application.port.out.KakaoUnlinkException;
import com.orbit.auth.application.port.out.KakaoUnlinkException.Failure;
import com.orbit.support.KakaoAdminApiStub;

@DisplayName("카카오 연결 끊기 클라이언트")
class KakaoUnlinkClientTest {

    private static final String USER_ID = "4012345678";

    private static KakaoAdminApiStub kakao;
    private static KakaoUnlinkClient client;

    @BeforeAll
    static void startStub() {
        kakao = KakaoAdminApiStub.start();
        client = new KakaoUnlinkClient(
                new KakaoAdminApiProperties(kakao.unlinkUri(), KakaoAdminApiStub.ADMIN_KEY, Duration.ofSeconds(2)));
    }

    @AfterAll
    static void stopStub() {
        kakao.close();
    }

    @BeforeEach
    void resetStub() {
        kakao.reset();
    }

    @Test
    @DisplayName("서비스 앱 어드민 키로 회원번호의 연결을 끊는다")
    void unlinksUserWithAdminKey() {
        client.unlink(USER_ID);

        KakaoAdminApiStub.Request request = kakao.requests().getLast();
        assertThat(request.authorization()).isEqualTo("KakaoAK " + KakaoAdminApiStub.ADMIN_KEY);
        assertThat(request.form()).containsEntry("target_id_type", "user_id").containsEntry("target_id", USER_ID);
    }

    @Test
    @DisplayName("이미 앱과 연결되지 않은 사용자(-101)는 끊긴 것으로 본다")
    void treatsNotLinkedUserAsUnlinked() {
        kakao.failNext(400, -101);

        assertThatCode(() -> client.unlink(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("휴면·없는 계정(-103)과 잘못된 인자(-2)는 카카오가 거절한 것이다")
    void rejectsWhenKakaoRefuses() {
        kakao.failNext(400, -103);
        assertFailure(Failure.REJECTED);

        kakao.failNext(400, -2);
        assertFailure(Failure.REJECTED);
    }

    @Test
    @DisplayName("어드민 키 오류(-401)·서비스 점검·서버 오류는 다시 시도할 실패다")
    void failsTemporarilyOnKeyOrServerError() {
        kakao.failNext(401, -401);
        assertFailure(Failure.UNAVAILABLE);

        kakao.failNext(503, -9798);
        assertFailure(Failure.UNAVAILABLE);

        kakao.respondNext(500, "not json");
        assertFailure(Failure.UNAVAILABLE);
    }

    @Test
    @DisplayName("연결하지 못하면 다시 시도할 실패다")
    void failsTemporarilyWhenUnreachable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        KakaoUnlinkClient unreachable = new KakaoUnlinkClient(new KakaoAdminApiProperties(
                "http://127.0.0.1:" + closedPort + "/v1/user/unlink",
                KakaoAdminApiStub.ADMIN_KEY,
                Duration.ofSeconds(1)));

        assertThatThrownBy(() -> unreachable.unlink(USER_ID))
                .isInstanceOfSatisfying(KakaoUnlinkException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(Failure.UNAVAILABLE));
    }

    @Test
    @DisplayName("실패 메시지와 설정 문자열에 회원번호·어드민 키를 넣지 않는다")
    void hidesUserIdAndAdminKey() {
        kakao.failNext(400, -103);

        assertThatThrownBy(() -> client.unlink(USER_ID))
                .hasMessageNotContaining(USER_ID)
                .hasMessageNotContaining(KakaoAdminApiStub.ADMIN_KEY);
        assertThat(new KakaoAdminApiProperties(kakao.unlinkUri(), KakaoAdminApiStub.ADMIN_KEY, Duration.ofSeconds(1))
                        .toString())
                .doesNotContain(KakaoAdminApiStub.ADMIN_KEY);
    }

    @Test
    @DisplayName("주소·어드민 키·제한 시간이 비어 있으면 기동하지 않는다")
    void rejectsIncompleteProperties() {
        assertThatThrownBy(() -> new KakaoAdminApiProperties(" ", "key", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.auth.kakao.admin-api.unlink-uri must not be blank");
        assertThatThrownBy(() -> new KakaoAdminApiProperties("http://localhost", " ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.auth.kakao.admin-api.admin-key must not be blank");
        assertThatThrownBy(() -> new KakaoAdminApiProperties("http://localhost", "key", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("app.auth.kakao.admin-api.timeout must be positive");
    }

    private void assertFailure(Failure expected) {
        assertThatThrownBy(() -> client.unlink(USER_ID))
                .isInstanceOfSatisfying(KakaoUnlinkException.class, exception -> assertThat(exception.failure())
                        .isEqualTo(expected));
    }
}
