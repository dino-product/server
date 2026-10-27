package com.orbit.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.orbit.media.application.port.out.ObjectStorageUnavailableException;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 저장소 응답 상태의 해석과 장애 기록(로그·지표). LocalStack은 IAM을 검사하지 않아 403을 만들 수 없고 5xx·제한 시간 초과도 재현하기 어려워 클라이언트를 대체한다.
 * 서명 실패는 실제 서명기에 자격 증명을 내주지 못하는 공급자를 넣어 만든다.
 */
@DisplayName("S3 저장소 어댑터의 응답 상태 해석과 장애 기록")
@ExtendWith(OutputCaptureExtension.class)
class S3ObjectStorageAdapterStatusTest {

    private static final String KEY = "incoming/0b6a1f7e-2a4c-4f6e-9d3b-1c2d3e4f5a61";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final S3Client s3 = mock(S3Client.class, org.mockito.Mockito.CALLS_REAL_METHODS);

    @Test
    @DisplayName("없는 키의 HeadObject 404는 아직 올라오지 않은 것으로 보고 결과 missing으로 기록한다")
    void missingObject(CapturedOutput output) {
        answering(S3Exception.builder().statusCode(404).build());

        assertThat(adapter().find(KEY)).isEmpty();

        assertThat(calls("head_object", "missing", "404").count()).isEqualTo(1);
        assertThat(output).doesNotContain("media storage call failed");
    }

    /** 앱 역할에 ListBucket이 있어 없는 키는 404로 온다. 403은 권한 설정 오류라 업로드 전으로 가리지 않고 장애로 드러낸다. */
    @ParameterizedTest
    @ValueSource(ints = {400, 403, 500, 503})
    @DisplayName("403을 포함한 그 밖의 오류 응답은 원인을 이은 저장소 장애이고, 작업·키·상태 코드·S3 오류 코드를 경고와 지표로 남긴다")
    void otherErrors(int status, CapturedOutput output) {
        S3Exception error = (S3Exception) S3Exception.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("SlowDown").build())
                .build();
        answering(error);

        assertThatThrownBy(() -> adapter().find(KEY))
                .isInstanceOf(ObjectStorageUnavailableException.class)
                .hasCause(error);

        assertThat(calls("head_object", "error", String.valueOf(status)).count())
                .isEqualTo(1);
        assertThat(output).contains("operation=head_object", "key=" + KEY, "status=" + status, "errorCode=SlowDown");
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 412})
    @DisplayName("앞부분 읽기·복사가 404(지워짐)나 412(확인한 뒤 바뀜)면 하지 않고 결과 missing으로 기록한다")
    void readAndCopyOfMissingOrChangedObject(int status, CapturedOutput output) {
        S3Exception error =
                (S3Exception) S3Exception.builder().statusCode(status).build();
        doThrow(error).when(s3).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        doThrow(error).when(s3).copyObject(any(CopyObjectRequest.class));

        assertThat(adapter().readFirstBytes(KEY, 16, "\"etag\"")).isEmpty();
        assertThat(adapter().copy(KEY, "media/1/x", "\"etag\"", "image/png")).isFalse();

        assertThat(calls("get_object_range", "missing", String.valueOf(status)).count())
                .isEqualTo(1);
        assertThat(calls("copy_object", "missing", String.valueOf(status)).count())
                .isEqualTo(1);
        assertThat(output).doesNotContain("media storage call failed");
    }

    @Test
    @DisplayName("앞부분 읽기·복사의 그 밖의 오류 응답은 저장소 장애다")
    void readAndCopyErrors() {
        S3Exception error = (S3Exception) S3Exception.builder().statusCode(500).build();
        doThrow(error).when(s3).getObject(any(GetObjectRequest.class), any(ResponseTransformer.class));
        doThrow(error).when(s3).copyObject(any(CopyObjectRequest.class));

        assertThatThrownBy(() -> adapter().readFirstBytes(KEY, 16, "\"etag\""))
                .isInstanceOf(ObjectStorageUnavailableException.class);
        assertThatThrownBy(() -> adapter().copy(KEY, "media/1/x", "\"etag\"", "image/png"))
                .isInstanceOf(ObjectStorageUnavailableException.class);
        assertThat(calls("get_object_range", "error", "500").count()).isEqualTo(1);
        assertThat(calls("copy_object", "error", "500").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("오류 응답 없이 실패하면(제한 시간 초과) 상태 코드 none으로 기록한다")
    void timeout(CapturedOutput output) {
        answering(ApiCallTimeoutException.create(2_000));

        assertThatThrownBy(() -> adapter().find(KEY)).isInstanceOf(ObjectStorageUnavailableException.class);

        assertThat(calls("head_object", "error", "none").count()).isEqualTo(1);
        assertThat(output).contains("operation=head_object", "status=none");
    }

    @Test
    @DisplayName("성공한 호출은 결과 success, 상태 코드 none으로 기록한다")
    void success() {
        org.mockito.Mockito.doReturn(HeadObjectResponse.builder()
                        .contentLength(16L)
                        .contentType("image/png")
                        .build())
                .when(s3)
                .headObject(any(HeadObjectRequest.class));

        assertThat(adapter().find(KEY)).isPresent();

        assertThat(calls("head_object", "success", "none").count()).isEqualTo(1);
    }

    /** 복사는 S3 서버 쪽 시간이 객체 크기에 비례해, 클라이언트 전체의 제한 시간(시도당 3초)이면 큰 사진이 매번 시간 초과로 끝난다. */
    @Test
    @DisplayName("복사만 호출·시도 제한 시간을 copy-timeout으로 바꾸고, 다른 저장소 호출은 클라이언트의 제한 시간을 그대로 쓴다")
    void onlyCopyUsesCopyTimeout() {
        Duration copyTimeout = Duration.ofSeconds(45);
        S3ObjectStorageAdapter adapter =
                new S3ObjectStorageAdapter(s3, mock(S3Presigner.class), properties(copyTimeout), meters);
        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        ArgumentCaptor<HeadObjectRequest> head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        ArgumentCaptor<GetObjectRequest> read = ArgumentCaptor.forClass(GetObjectRequest.class);
        ArgumentCaptor<PutObjectTaggingRequest> tagging = ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        doReturn(CopyObjectResponse.builder().build()).when(s3).copyObject(copy.capture());
        doReturn(HeadObjectResponse.builder()
                        .contentLength(16L)
                        .contentType("image/png")
                        .build())
                .when(s3)
                .headObject(head.capture());
        doReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[16]))
                .when(s3)
                .getObject(read.capture(), any(ResponseTransformer.class));
        doReturn(PutObjectTaggingResponse.builder().build()).when(s3).putObjectTagging(tagging.capture());

        assertThat(adapter.copy(KEY, "media/1/x", "\"etag\"", "image/png")).isTrue();
        assertThat(adapter.find(KEY)).isPresent();
        assertThat(adapter.readFirstBytes(KEY, 16, "\"etag\"")).isPresent();
        adapter.markExpired("media/1/x");

        AwsRequestOverrideConfiguration copyOverride =
                copy.getValue().overrideConfiguration().orElseThrow();
        assertThat(copyOverride.apiCallTimeout()).contains(copyTimeout);
        assertThat(copyOverride.apiCallAttemptTimeout()).contains(copyTimeout);
        assertThat(head.getValue().overrideConfiguration()).isEmpty();
        assertThat(read.getValue().overrideConfiguration()).isEmpty();
        assertThat(tagging.getValue().overrideConfiguration()).isEmpty();
    }

    @Test
    @DisplayName("자격 증명을 얻지 못해 업로드·내려받기 주소를 서명하지 못하면 저장소 장애이고, 서명 주소 없이 경고와 지표를 남긴다")
    void signingFailure(CapturedOutput output) {
        S3StorageProperties properties = properties();
        try (S3Presigner failing = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .endpointOverride(properties.endpoint())
                .credentialsProvider(() -> {
                    throw SdkClientException.create(
                            "Unable to load credentials from any of the providers in the chain");
                })
                .build()) {
            S3ObjectStorageAdapter adapter = new S3ObjectStorageAdapter(s3, failing, properties, meters);

            assertThatThrownBy(() -> adapter.presignUpload(KEY, "image/png", 16))
                    .isInstanceOf(ObjectStorageUnavailableException.class)
                    .hasCauseInstanceOf(SdkClientException.class);
            assertThatThrownBy(() -> adapter.presignDownload("media/1/x"))
                    .isInstanceOf(ObjectStorageUnavailableException.class);
        }

        assertThat(calls("presign_put_object", "error", "none").count()).isEqualTo(1);
        assertThat(calls("presign_get_object", "error", "none").count()).isEqualTo(1);
        assertThat(output)
                .contains("operation=presign_put_object", "key=" + KEY, "operation=presign_get_object")
                .doesNotContain("X-Amz-Signature");
    }

    /** 소비자를 받는 오버로드는 실제 기본 메서드로 두고, 요청 객체를 받는 호출만 대체한다. */
    private void answering(Throwable error) {
        doThrow(error).when(s3).headObject(any(HeadObjectRequest.class));
    }

    private Timer calls(String operation, String outcome, String status) {
        return meters.get(S3ObjectStorageAdapter.CALLS_METRIC)
                .tags("operation", operation, "outcome", outcome, "status", status)
                .timer();
    }

    private S3ObjectStorageAdapter adapter() {
        return new S3ObjectStorageAdapter(s3, mock(S3Presigner.class), properties(), meters);
    }

    private static S3StorageProperties properties() {
        return properties(null);
    }

    private static S3StorageProperties properties(Duration copyTimeout) {
        return new S3StorageProperties(
                "bucket",
                "ap-northeast-2",
                URI.create("http://127.0.0.1:9"),
                true,
                "test",
                "test",
                null,
                null,
                null,
                null,
                copyTimeout);
    }
}
