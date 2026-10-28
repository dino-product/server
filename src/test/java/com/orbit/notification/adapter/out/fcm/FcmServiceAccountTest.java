package com.orbit.notification.adapter.out.fcm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FCM 서비스 계정 키")
class FcmServiceAccountTest {

    private FcmStub stub;

    @BeforeEach
    void setUp() throws Exception {
        stub = new FcmStub();
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    @DisplayName("base64로 인코딩한 서비스 계정 키에서 프로젝트·계정·키·토큰 엔드포인트를 읽는다")
    void readsServiceAccountFields() {
        FcmServiceAccount account = FcmServiceAccount.fromBase64Json(stub.credentials());

        assertThat(account.projectId()).isEqualTo(FcmStub.PROJECT_ID);
        assertThat(account.clientEmail()).isEqualTo(FcmStub.CLIENT_EMAIL);
        assertThat(account.privateKeyId()).isEqualTo(FcmStub.PRIVATE_KEY_ID);
        assertThat(account.privateKey().getAlgorithm()).isEqualTo("RSA");
        assertThat(account.tokenUri()).isEqualTo(stub.tokenUri());
    }

    @Test
    @DisplayName("키에 토큰 엔드포인트가 없으면 Google 기본 주소를 쓴다")
    void usesGoogleTokenEndpointByDefault() {
        Map<String, Object> json = stub.serviceAccount();
        json.remove("token_uri");

        assertThat(FcmServiceAccount.fromBase64Json(FcmStub.credentials(json)).tokenUri())
                .isEqualTo("https://oauth2.googleapis.com/token");
    }

    @Test
    @DisplayName("base64·JSON이 아니거나 필수 값·RSA 키가 없으면 내용을 드러내지 않고 실패한다")
    void rejectsMalformedCredentialsWithoutLeakingThem() {
        String notBase64 = "not base64 SECRET!";
        String notJson = Base64.getEncoder().encodeToString("SECRET".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> missingProject = stub.serviceAccount();
        missingProject.remove("project_id");
        Map<String, Object> badKey = stub.serviceAccount();
        badKey.put("private_key", "-----BEGIN PRIVATE KEY-----\nSECRET\n-----END PRIVATE KEY-----\n");

        assertThatThrownBy(() -> FcmServiceAccount.fromBase64Json(notBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("SECRET")
                .hasNoCause();
        assertThatThrownBy(() -> FcmServiceAccount.fromBase64Json(notJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("SECRET")
                .hasNoCause();
        assertThatThrownBy(() -> FcmServiceAccount.fromBase64Json(FcmStub.credentials(missingProject)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("project_id");
        assertThatThrownBy(() -> FcmServiceAccount.fromBase64Json(FcmStub.credentials(badKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("SECRET")
                .hasNoCause();
    }

    @Test
    @DisplayName("문자열 표현에 개인키를 남기지 않는다")
    void masksPrivateKeyInStringForm() {
        FcmServiceAccount account = FcmServiceAccount.fromBase64Json(stub.credentials());

        assertThat(account.toString())
                .contains(FcmStub.PROJECT_ID)
                .doesNotContain(FcmStub.PRIVATE_KEY_ID)
                .doesNotContain("PRIVATE KEY")
                .doesNotContain(account.privateKey().toString());
    }
}
