package com.orbit.media.adapter.out.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.orbit.media.application.port.out.ObjectStoragePort;
import com.orbit.media.application.port.out.ObjectStorageUnavailableException;
import com.orbit.media.application.port.out.PresignedUpload;
import com.orbit.media.application.port.out.StoredObject;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.MetadataDirective;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * S3(와 호환 저장소) 구현. 모든 프로필에서 등록하며 로컬·테스트는 설정으로 LocalStack 주소를 준다. 업로드 서명에는 {@code Content-Type}과
 * {@code Content-Length}를 넣어 밝힌 형식·크기로만 올릴 수 있게 한다. 앱 권한에는 객체 삭제가 없다(ADR-007).
 *
 * <p>저장소 호출과 주소 서명마다 걸린 시간과 결과를 타이머 {@value #CALLS_METRIC}(태그 {@code operation}·{@code outcome}·{@code status})로
 * 남긴다. 결과는 {@code success}, 객체가 없거나 확인한 뒤 바뀜({@code missing}, 404·412), 저장소 장애({@code error})이고, {@code status}는
 * S3가 오류로 답한 HTTP 상태 코드이며 오류 응답이 없으면(성공·제한 시간 초과·연결·자격 증명 실패) {@code none}이다. 저장소 장애는 작업 종류·객체 키·상태
 * 코드·S3 오류 코드를 WARN으로 남긴다. 자격 증명과 서명한 주소는 남기지 않는다.
 */
@Component
class S3ObjectStorageAdapter implements ObjectStoragePort {

    static final String CALLS_METRIC = "media.storage.calls";
    static final Tag EXPIRED_TAG = Tag.builder().key("status").value("expired").build();

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorageAdapter.class);
    private static final int NOT_FOUND = 404;
    private static final int PRECONDITION_FAILED = 412;
    private static final String NO_STATUS = "none";
    private static final String INLINE = "inline";

    /** 저장소 작업 종류(지표 태그·로그 값). */
    enum Operation {
        HEAD_OBJECT("head_object"),
        GET_OBJECT_RANGE("get_object_range"),
        COPY_OBJECT("copy_object"),
        PUT_OBJECT_TAGGING("put_object_tagging"),
        /** 저장소를 부르지 않지만 자격 증명을 얻어야 해 실패할 수 있다. */
        PRESIGN_PUT_OBJECT("presign_put_object"),
        PRESIGN_GET_OBJECT("presign_get_object");

        private final String tag;

        Operation(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3StorageProperties properties;
    private final MeterRegistry meterRegistry;

    S3ObjectStorageAdapter(
            S3Client mediaS3Client,
            S3Presigner mediaS3Presigner,
            S3StorageProperties properties,
            MeterRegistry meterRegistry) {
        this.s3 = mediaS3Client;
        this.presigner = mediaS3Presigner;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, long contentLength) {
        PresignedPutObjectRequest presigned = call(
                        Operation.PRESIGN_PUT_OBJECT,
                        key,
                        Set.of(),
                        () -> presigner.presignPutObject(request -> request.signatureDuration(properties.uploadUrlTtl())
                                .putObjectRequest(put -> put.bucket(properties.bucket())
                                        .key(key)
                                        .contentType(contentType)
                                        .contentLength(contentLength))))
                .orElseThrow();
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            if (!name.equalsIgnoreCase("host")) {
                headers.put(name, String.join(",", values));
            }
        });
        return new PresignedUpload(presigned.url().toString(), headers, presigned.expiration());
    }

    /**
     * 없는 객체는 404다. 앱 역할에 ListBucket 권한이 있어 S3가 없는 키를 404로 답한다(ListBucket이 없으면 403). 403은 "아직 올라오지 않음"이 아니라 권한 설정
     * 오류이므로 저장소 장애로 보고 경고·지표로 드러낸다.
     */
    @Override
    public Optional<StoredObject> find(String key) {
        return call(Operation.HEAD_OBJECT, key, Set.of(NOT_FOUND), () -> {
            HeadObjectResponse head =
                    s3.headObject(request -> request.bucket(properties.bucket()).key(key));
            return new StoredObject(head.contentLength(), head.contentType(), head.eTag());
        });
    }

    /** 지워졌으면 404, 확인한 뒤 다른 내용으로 바뀌었으면 {@code If-Match}가 맞지 않아 412다. 둘 다 객체가 없는 것으로 본다. */
    @Override
    public Optional<byte[]> readFirstBytes(String key, int length, String eTag) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        return call(Operation.GET_OBJECT_RANGE, key, Set.of(NOT_FOUND, PRECONDITION_FAILED), () -> s3.getObjectAsBytes(
                        request -> request.bucket(properties.bucket())
                                .key(key)
                                .range("bytes=0-" + (length - 1))
                                .ifMatch(eTag))
                .asByteArray());
    }

    /**
     * 원본이 지워졌으면 404, 확인한 뒤 다른 내용으로 바뀌었으면 {@code x-amz-copy-source-if-match}가 맞지 않아 412다. 둘 다 복사하지 않는다.
     *
     * <p>대상 메타데이터는 원본을 복사하지 않고 바꾼다({@code REPLACE}). presigned PUT은 서명한 {@code Content-Type}·{@code Content-Length}
     * 밖의 헤더({@code Content-Disposition}·{@code Cache-Control}·{@code Content-Language} 등)도 업로더가 붙여 저장할 수 있어, 그대로 두면
     * 내려받기 응답에 업로더가 고른 헤더가 쓰인다. 그래서 형식은 발급 때 기록하고 확인한 값, 표시는 {@code inline}, 캐시는 {@link #cacheControl()}로
     * 고정하고 사용자 메타데이터는 버린다.
     */
    @Override
    public boolean copy(String sourceKey, String targetKey, String eTag, String contentType) {
        return call(
                        Operation.COPY_OBJECT,
                        sourceKey,
                        Set.of(NOT_FOUND, PRECONDITION_FAILED),
                        () -> s3.copyObject(request -> request.sourceBucket(properties.bucket())
                                .sourceKey(sourceKey)
                                .copySourceIfMatch(eTag)
                                .destinationBucket(properties.bucket())
                                .destinationKey(targetKey)
                                .metadataDirective(MetadataDirective.REPLACE)
                                .contentType(contentType)
                                .cacheControl(cacheControl())
                                .contentDisposition(INLINE)))
                .isPresent();
    }

    @Override
    public void markExpired(String key) {
        call(
                Operation.PUT_OBJECT_TAGGING,
                key,
                Set.of(),
                () -> s3.putObjectTagging(request -> request.bucket(properties.bucket())
                        .key(key)
                        .tagging(Tagging.builder().tagSet(List.of(EXPIRED_TAG)).build())));
    }

    @Override
    public String presignDownload(String key) {
        return call(Operation.PRESIGN_GET_OBJECT, key, Set.of(), () -> presigner
                        .presignGetObject(request -> request.signatureDuration(properties.downloadUrlTtl())
                                .getObjectRequest(
                                        get -> get.bucket(properties.bucket()).key(key)))
                        .url()
                        .toString())
                .orElseThrow();
    }

    /**
     * 연결한 사진의 {@code Cache-Control}. 사진은 권한 확인을 거친 서명 주소로만 열리므로 공유 캐시(프록시·CDN)가 저장하지 않게 {@code private}으로 두고, 브라우저
     * 캐시는 그 주소가 유효한 동안({@code app.media.storage.download-url-ttl}, 기본 10분)만 쓴다. 서명 주소는 발급마다 달라 그보다 오래 둬도 다시 쓰이지 않고,
     * 만료된 주소의 응답을 브라우저가 계속 보여 주는 기간만 길어진다. 복사할 때의 값으로 고정되므로 설정을 바꾸면 그 뒤에 연결한 사진부터 적용된다.
     */
    private String cacheControl() {
        return "private, max-age=" + properties.downloadUrlTtl().toSeconds();
    }

    /**
     * 저장소 호출(또는 주소 서명) 하나를 실행하고 시간·결과를 기록한다. {@code missingStatuses}의 상태 코드로 온 S3 오류는 객체가 없는 것으로 보고 빈 결과를
     * 돌려준다. 그 밖의 SDK 오류(오류 응답, 제한 시간 초과, 연결·자격 증명 실패)는 경고를 남기고 {@link ObjectStorageUnavailableException}으로 바꾼다.
     *
     * @param key 로그에 남길 객체 키(복사는 원본 키)
     */
    private <T> Optional<T> call(Operation operation, String key, Set<Integer> missingStatuses, Supplier<T> request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = request.get();
            record(sample, operation, "success", NO_STATUS);
            return Optional.of(result);
        } catch (S3Exception exception) {
            String status = String.valueOf(exception.statusCode());
            if (missingStatuses.contains(exception.statusCode())) {
                record(sample, operation, "missing", status);
                return Optional.empty();
            }
            record(sample, operation, "error", status);
            throw unavailable(operation, key, status, exception);
        } catch (SdkException exception) {
            record(sample, operation, "error", NO_STATUS);
            throw unavailable(operation, key, NO_STATUS, exception);
        }
    }

    private void record(Timer.Sample sample, Operation operation, String outcome, String status) {
        sample.stop(Timer.builder(CALLS_METRIC)
                .description("파일 저장소(S3) 호출 시간과 결과")
                .tag("operation", operation.tag())
                .tag("outcome", outcome)
                .tag("status", status)
                .register(meterRegistry));
    }

    /** 원인 예외의 메시지에는 서비스·상태 코드·요청 ID만 들어 있고 자격 증명·서명은 없다. 스택은 원인으로 이어 둔다. */
    private static ObjectStorageUnavailableException unavailable(
            Operation operation, String key, String status, SdkException exception) {
        String errorCode = exception instanceof S3Exception s3Exception
                ? Optional.ofNullable(s3Exception.awsErrorDetails())
                        .map(AwsErrorDetails::errorCode)
                        .orElse(NO_STATUS)
                : NO_STATUS;
        log.warn(
                "media storage call failed: operation={}, key={}, status={}, errorCode={}, cause={}",
                operation.tag(),
                key,
                status,
                errorCode,
                exception.toString());
        return new ObjectStorageUnavailableException(exception);
    }
}
