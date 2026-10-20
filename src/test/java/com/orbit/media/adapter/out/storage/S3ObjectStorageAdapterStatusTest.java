package com.orbit.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.net.URI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.orbit.media.application.port.out.ObjectStorageUnavailableException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 저장소 응답 상태의 해석. LocalStack은 IAM을 검사하지 않아 403(권한 범위 밖의 없는 키)을 만들 수 없어 클라이언트를 대체한다. */
@DisplayName("S3 저장소 어댑터의 응답 상태 해석")
class S3ObjectStorageAdapterStatusTest {

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    @DisplayName("없는 키의 HeadObject가 403이나 404면 아직 올라오지 않은 것으로 본다")
    void missingObject(int status) {
        assertThat(adapterAnswering(status).find("incoming/x")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 500, 503})
    @DisplayName("그 밖의 오류 응답은 저장소 장애다")
    void otherErrors(int status) {
        assertThatThrownBy(() -> adapterAnswering(status).find("incoming/x"))
                .isInstanceOf(ObjectStorageUnavailableException.class);
    }

    private static S3ObjectStorageAdapter adapterAnswering(int status) {
        S3Client s3 = mock(S3Client.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        // 소비자를 받는 오버로드는 실제 기본 메서드로 두고, 요청 객체를 받는 호출만 대체한다.
        doThrow(S3Exception.builder().statusCode(status).build()).when(s3).headObject(any(HeadObjectRequest.class));
        S3StorageProperties properties = new S3StorageProperties(
                "bucket",
                "ap-northeast-2",
                URI.create("http://127.0.0.1:9"),
                true,
                "test",
                "test",
                null,
                null,
                null,
                null);
        return new S3ObjectStorageAdapter(s3, mock(S3Presigner.class), properties);
    }
}
