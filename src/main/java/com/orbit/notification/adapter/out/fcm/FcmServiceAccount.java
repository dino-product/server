package com.orbit.notification.adapter.out.fcm;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Firebase 서비스 계정 키(JSON)에서 FCM 호출에 필요한 값만 읽는다. 키가 정해 둔 토큰 엔드포인트가 없으면 Google 기본 주소를 쓴다. 키 내용은 오류 메시지와
 * 문자열 표현에 남기지 않는다.
 */
record FcmServiceAccount(
        String projectId, String clientEmail, String privateKeyId, RSAPrivateKey privateKey, String tokenUri) {

    static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String PROPERTY = "app.notification.push.fcm.credentials";
    private static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    /** base64로 인코딩한 서비스 계정 키를 읽는다. 형식이 틀리면 기동이 실패하도록 {@link IllegalStateException}을 던진다. */
    static FcmServiceAccount fromBase64Json(String base64Json) {
        Map<String, Object> json;
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Json.strip());
            json = JsonMapper.builder().build().readValue(new String(decoded, StandardCharsets.UTF_8), JSON_OBJECT);
        } catch (IllegalArgumentException | JacksonException exception) {
            // 키 내용이 로그에 남지 않도록 원인 예외를 붙이지 않는다.
            throw new IllegalStateException(PROPERTY + " is not a base64-encoded service account JSON");
        }
        String tokenUri = text(json, "token_uri");
        return new FcmServiceAccount(
                required(json, "project_id"),
                required(json, "client_email"),
                required(json, "private_key_id"),
                parsePrivateKey(required(json, "private_key")),
                tokenUri == null ? DEFAULT_TOKEN_URI : tokenUri);
    }

    private static String required(Map<String, Object> json, String field) {
        String value = text(json, field);
        if (value == null) {
            throw new IllegalStateException(PROPERTY + " has no " + field);
        }
        return value;
    }

    private static String text(Map<String, Object> json, String field) {
        return json.get(field) instanceof String value && !value.isBlank() ? value : null;
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        String base64 = pem.replace("\\n", "\n")
                .replace(PEM_HEADER, "")
                .replace(PEM_FOOTER, "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException(PROPERTY + " has no PKCS#8 RSA private_key");
        }
    }

    @Override
    public String toString() {
        return "FcmServiceAccount[projectId=" + projectId + ", clientEmail=" + clientEmail + ", privateKeyId=***"
                + ", privateKey=***, tokenUri=" + tokenUri + "]";
    }
}
