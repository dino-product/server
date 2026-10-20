package com.orbit.media.adapter.out.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.orbit.media.application.port.out.ObjectStoragePort;
import com.orbit.media.application.port.out.ObjectStorageUnavailableException;
import com.orbit.media.application.port.out.PresignedUpload;
import com.orbit.media.application.port.out.StoredObject;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * S3(와 호환 저장소) 구현. 모든 프로필에서 등록하며 로컬·테스트는 설정으로 LocalStack 주소를 준다. 업로드 서명에는 {@code Content-Type}과
 * {@code Content-Length}를 넣어 밝힌 형식·크기로만 올릴 수 있게 한다. 앱 권한에는 객체 삭제가 없다(ADR-006).
 */
@Component
class S3ObjectStorageAdapter implements ObjectStoragePort {

    static final Tag EXPIRED_TAG = Tag.builder().key("status").value("expired").build();
    private static final int NOT_FOUND = 404;
    private static final int FORBIDDEN = 403;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3StorageProperties properties;

    S3ObjectStorageAdapter(S3Client mediaS3Client, S3Presigner mediaS3Presigner, S3StorageProperties properties) {
        this.s3 = mediaS3Client;
        this.presigner = mediaS3Presigner;
        this.properties = properties;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long contentLength) {
        PresignedPutObjectRequest presigned =
                presigner.presignPutObject(request -> request.signatureDuration(properties.uploadUrlTtl())
                        .putObjectRequest(put -> put.bucket(properties.bucket())
                                .key(key)
                                .contentType(contentType)
                                .contentLength(contentLength)));
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host")) {
                headers.put(name, String.join(",", values));
            }
        });
        return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
    }

    /**
     * 없는 객체는 404 대신 403으로 올 수 있다. S3는 ListBucket 권한이 없으면 없는 키의 HeadObject를 403으로 답하는데, 앱 권한의 ListBucket은 접두사 조건으로
     * 제한돼 있어 HeadObject 요청에는 적용되지 않을 수 있다. 있는 객체는 GetObject 권한으로 읽히므로 403도 "아직 올라오지 않음"으로 본다.
     */
    @Override
    public Optional<StoredObject> find(String key) {
        try {
            HeadObjectResponse head =
                    s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
        } catch (S3Exception exception) {
            if (exception.statusCode() == NOT_FOUND || exception.statusCode() == FORBIDDEN) {
                return Optional.empty();
            }
            throw new ObjectStorageUnavailableException(exception);
        } catch (SdkException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    @Override
    public byte[] readFirstBytes(String key, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        try {
            return s3.getObjectAsBytes(request ->
                            request.bucket(properties.bucket()).key(key).range("bytes=0-" + (length - 1)))
                    .asByteArray();
        } catch (SdkException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        try {
            s3.copyObject(request -> request.sourceBucket(properties.bucket())
                    .sourceKey(sourceKey)
                    .destinationBucket(properties.bucket())
                    .destinationKey(targetKey));
        } catch (SdkException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    @Override
    public void markExpired(String key) {
        try {
            s3.putObjectTagging(request -> request.bucket(properties.bucket())
                    .key(key)
                    .tagging(Tagging.builder().tagSet(List.of(EXPIRED_TAG)).build()));
        } catch (SdkException exception) {
            throw new ObjectStorageUnavailableException(exception);
        }
    }

    @Override
    public String presignDownload(String key) {
        return presigner
                .presignGetObject(request -> request.signatureDuration(properties.downloadUrlTtl())
                        .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)))
                .url()
                .toString();
    }
}
