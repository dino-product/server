package com.orbit.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.orbit.media.application.port.out.ObjectStorageUnavailableException;
import com.orbit.media.application.port.out.PresignedUpload;
import com.orbit.media.application.port.out.StoredObject;
import com.orbit.support.LocalStackTestSupport;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@DisplayName("S3 저장소 어댑터(LocalStack)")
@Testcontainers(disabledWithoutDocker = true)
class S3ObjectStorageAdapterTest {

    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'
    };

    private static AwsCredentialsProvider credentials;
    private static S3Client s3;
    private static S3Presigner presigner;
    private static S3ObjectStorageAdapter adapter;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void connect() {
        S3StorageProperties properties = properties(URI.create(LocalStackTestSupport.endpoint()));
        S3ClientConfiguration configuration = new S3ClientConfiguration();
        credentials = configuration.mediaCredentialsProvider(properties);
        s3 = configuration.mediaS3Client(properties, credentials);
        presigner = configuration.mediaS3Presigner(properties, credentials);
        adapter = new S3ObjectStorageAdapter(s3, presigner, properties, new SimpleMeterRegistry());
    }

    @AfterAll
    static void close() {
        if (s3 != null) {
            s3.close();
            presigner.close();
        }
    }

    @Test
    @DisplayName("업로드 주소 서명에 Content-Type과 Content-Length가 들어가고 응답 헤더로 돌려준다")
    void signsContentTypeAndLength() {
        PresignedUpload upload = adapter.presignUpload(key(), "image/png", PNG.length);

        String signedHeaders = queryParameter(upload.url(), "X-Amz-SignedHeaders");
        assertThat(signedHeaders.split(";")).contains("host", "content-type", "content-length");
        // 서명기는 헤더 이름을 소문자로 돌려준다(HTTP 헤더 이름은 대소문자를 가리지 않음).
        assertThat(upload.headers())
                .containsOnly(Map.entry("content-type", "image/png"), Map.entry("content-length", "16"));
        assertThat(upload.expiresAt()).isAfter(java.time.Instant.now().plus(Duration.ofMinutes(9)));
    }

    @Test
    @DisplayName("밝힌 형식·크기로 올린 객체를 메타데이터·앞부분으로 읽고 복사·만료 표시·내려받기 주소를 만든다")
    void uploadsAndReadsObject() throws Exception {
        String key = key();
        PresignedUpload upload = adapter.presignUpload(key, "image/png", PNG.length);

        assertThat(put(upload, "image/png", PNG).statusCode()).isEqualTo(200);

        StoredObject stored = adapter.find(key).orElseThrow();
        assertThat(stored.sizeBytes()).isEqualTo(PNG.length);
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.eTag()).isNotBlank();
        assertThat(adapter.readFirstBytes(key, 8, stored.eTag())).hasValue(Arrays.copyOf(PNG, 8));
        assertThat(adapter.readFirstBytes(key, 64, stored.eTag())).hasValue(PNG);

        String copied = key();
        assertThat(adapter.copy(key, copied, stored.eTag(), "image/png")).isTrue();
        assertThat(adapter.find(copied)).hasValueSatisfying(object -> {
            assertThat(object.sizeBytes()).isEqualTo(PNG.length);
            assertThat(object.contentType()).isEqualTo("image/png");
        });

        adapter.markExpired(copied);
        assertThat(s3.getObjectTagging(request ->
                                request.bucket(LocalStackTestSupport.BUCKET).key(copied))
                        .tagSet())
                .containsExactly(Tag.builder().key("status").value("expired").build());

        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(adapter.presignDownload(copied)))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("확인한 뒤 같은 키에 다른 내용을 다시 올리면 앞부분 읽기(If-Match)와 복사(copy-source-if-match)가 412로 막혀 하지 않는다")
    void bindsReadAndCopyToCheckedContent() throws Exception {
        String key = key();
        assertThat(put(adapter.presignUpload(key, "image/png", PNG.length), "image/png", PNG)
                        .statusCode())
                .isEqualTo(200);
        StoredObject checked = adapter.find(key).orElseThrow();

        byte[] replaced = PNG.clone();
        replaced[15] = 'X';
        assertThat(put(adapter.presignUpload(key, "image/png", replaced.length), "image/png", replaced)
                        .statusCode())
                .isEqualTo(200);
        String target = key();

        assertThat(adapter.readFirstBytes(key, 16, checked.eTag())).isEmpty();
        assertThat(adapter.copy(key, target, checked.eTag(), "image/png")).isFalse();
        assertThat(adapter.find(target)).isEmpty();

        String current = adapter.find(key).orElseThrow().eTag();
        assertThat(current).isNotEqualTo(checked.eTag());
        assertThat(adapter.readFirstBytes(key, 16, current)).hasValue(replaced);
        assertThat(adapter.copy(key, target, current, "image/png")).isTrue();
    }

    @Test
    @DisplayName("업로더가 서명 밖 헤더를 붙여 올려도 복사한 객체는 기록한 형식·정한 캐시·inline 표시로만 내려간다")
    void copyReplacesUploaderMetadata() throws Exception {
        String key = key();
        PresignedUpload upload = adapter.presignUpload(key, "image/png", PNG.length);
        Map<String, String> unsigned = Map.of(
                "Content-Disposition", "attachment; filename=\"photo.html\"",
                "Cache-Control", "public, max-age=31536000",
                "Content-Language", "ko");
        assertThat(put(upload, "image/png", PNG, unsigned).statusCode()).isEqualTo(200);
        HeadObjectResponse uploaded = s3.headObject(
                request -> request.bucket(LocalStackTestSupport.BUCKET).key(key));
        // 서명하지 않은 헤더도 저장된다(그래서 복사할 때 덮어써야 한다).
        assertThat(uploaded.contentDisposition()).isEqualTo("attachment; filename=\"photo.html\"");
        assertThat(uploaded.cacheControl()).isEqualTo("public, max-age=31536000");

        String copied = "media/1/" + UUID.randomUUID();
        assertThat(adapter.copy(key, copied, uploaded.eTag(), "image/png")).isTrue();

        HeadObjectResponse target = s3.headObject(
                request -> request.bucket(LocalStackTestSupport.BUCKET).key(copied));
        assertThat(target.contentType()).isEqualTo("image/png");
        assertThat(target.cacheControl()).isEqualTo("private, max-age=600");
        assertThat(target.contentDisposition()).isEqualTo("inline");
        assertThat(target.contentLanguage()).isNull();
        HttpResponse<byte[]> download = http.send(
                HttpRequest.newBuilder(URI.create(adapter.presignDownload(copied)))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(download.headers().firstValue("Cache-Control")).hasValue("private, max-age=600");
        assertThat(download.headers().firstValue("Content-Disposition")).hasValue("inline");
    }

    @Test
    @DisplayName("확인한 뒤 지워진 객체는 앞부분 읽기·복사가 404로 막혀 하지 않는다")
    void missingObjectIsNotReadOrCopied() {
        String missing = key();
        String target = key();

        assertThat(adapter.readFirstBytes(missing, 16, "\"0123456789abcdef0123456789abcdef\""))
                .isEmpty();
        assertThat(adapter.copy(missing, target, "\"0123456789abcdef0123456789abcdef\"", "image/png"))
                .isFalse();
        assertThat(adapter.find(target)).isEmpty();
    }

    @Test
    @DisplayName("밝힌 형식이나 크기와 다르게 올리면 저장소가 거부한다")
    void rejectsUploadOtherThanSigned() throws Exception {
        String key = key();
        PresignedUpload upload = adapter.presignUpload(key, "image/png", PNG.length);

        assertThat(put(upload, "image/jpeg", PNG).statusCode()).isEqualTo(403);
        assertThat(put(upload, "image/png", Arrays.copyOf(PNG, PNG.length + 1)).statusCode())
                .isEqualTo(403);
        assertThat(adapter.find(key)).isEmpty();
    }

    @Test
    @DisplayName("없는 객체는 빈 결과이고, 저장소에 닿지 못하면 저장소 장애 예외다")
    void reportsMissingAndUnreachableStorage() {
        assertThat(adapter.find(key())).isEmpty();

        S3StorageProperties unreachable = properties(URI.create("http://127.0.0.1:9"));
        S3ClientConfiguration configuration = new S3ClientConfiguration();
        try (S3Client client = configuration.mediaS3Client(unreachable, credentials)) {
            S3ObjectStorageAdapter down =
                    new S3ObjectStorageAdapter(client, presigner, unreachable, new SimpleMeterRegistry());
            assertThatThrownBy(() -> down.find(key())).isInstanceOf(ObjectStorageUnavailableException.class);
            assertThatThrownBy(() -> down.copy(key(), key(), "\"etag\"", "image/png"))
                    .isInstanceOf(ObjectStorageUnavailableException.class);
            assertThatThrownBy(() -> down.markExpired(key())).isInstanceOf(ObjectStorageUnavailableException.class);
        }
    }

    private HttpResponse<Void> put(PresignedUpload upload, String contentType, byte[] body)
            throws IOException, InterruptedException {
        return put(upload, contentType, body, Map.of());
    }

    /** {@code extraHeaders}는 서명하지 않은 헤더로 함께 보낸다. */
    private HttpResponse<Void> put(
            PresignedUpload upload, String contentType, byte[] body, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(URI.create(upload.url())).PUT(HttpRequest.BodyPublishers.ofByteArray(body));
        extraHeaders.forEach(request::header);
        // Content-Length는 HttpClient가 본문 길이로 직접 붙인다(제한 헤더).
        for (Map.Entry<String, String> header : upload.headers().entrySet()) {
            if (header.getKey().equalsIgnoreCase("content-type")) {
                request.header(header.getKey(), contentType);
            } else if (!header.getKey().equalsIgnoreCase("content-length")) {
                request.header(header.getKey(), header.getValue());
            }
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }

    private static String queryParameter(String url, String name) {
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError(name + " not in " + url);
    }

    private static String key() {
        return "incoming/" + UUID.randomUUID();
    }

    private static S3StorageProperties properties(URI endpoint) {
        return new S3StorageProperties(
                LocalStackTestSupport.BUCKET,
                LocalStackTestSupport.REGION,
                endpoint,
                true,
                "test",
                "test",
                null,
                null,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                null);
    }
}
