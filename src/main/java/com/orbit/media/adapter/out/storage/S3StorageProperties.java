package com.orbit.media.adapter.out.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3 저장소 설정. 배포 환경은 {@code MEDIA_BUCKET}·{@code MEDIA_REGION}(infra와 공유)만 주고 자격 증명은 기본 체인(노드 역할·IMDS)을
 * 쓴다. 로컬 LocalStack처럼 S3 호환 저장소는 {@code endpoint}·{@code pathStyleAccess}와 고정 자격 증명을 준다. 잘못된 값은 기동 시점에 거부한다.
 *
 * @param bucket 버킷 이름
 * @param region 리전(예: {@code ap-northeast-2})
 * @param endpoint S3 호환 저장소 주소. 비우면 AWS 기본 주소
 * @param pathStyleAccess 버킷을 경로에 두는 주소 형식(S3 호환 저장소용)
 * @param accessKeyId 고정 자격 증명(로컬·테스트 전용). 비우면 기본 자격 증명 체인
 * @param secretAccessKey 고정 자격 증명의 비밀 키
 * @param uploadUrlTtl 업로드 주소 유효 시간(1분 이상 1시간 이하, 없으면 10분)
 * @param downloadUrlTtl 내려받기 주소 유효 시간(1분 이상 1시간 이하, 없으면 10분)
 * @param apiCallTimeout 저장소 호출 하나의 전체 제한 시간(재시도 포함, 없으면 5초)
 * @param apiCallAttemptTimeout 시도 한 번의 제한 시간(없으면 3초)
 * @param copyTimeout 복사(CopyObject)의 호출·시도 제한 시간. S3 서버 쪽 복사 시간이 객체 크기에 비례해 다른 호출의 제한 시간 대신 쓴다.
 *     {@code apiCallTimeout} 이상이어야 하며 없으면 60초
 */
@ConfigurationProperties(prefix = "app.media.storage")
public record S3StorageProperties(
        String bucket,
        String region,
        URI endpoint,
        boolean pathStyleAccess,
        String accessKeyId,
        String secretAccessKey,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        Duration apiCallTimeout,
        Duration apiCallAttemptTimeout,
        Duration copyTimeout) {

    static final Duration DEFAULT_URL_TTL = Duration.ofMinutes(10);
    private static final Duration MIN_URL_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_URL_TTL = Duration.ofHours(1);
    private static final Duration DEFAULT_COPY_TIMEOUT = Duration.ofSeconds(60);

    public S3StorageProperties {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("app.media.storage.bucket(MEDIA_BUCKET) must not be blank");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("app.media.storage.region(MEDIA_REGION) must not be blank");
        }
        boolean hasAccessKey = accessKeyId != null && !accessKeyId.isBlank();
        boolean hasSecret = secretAccessKey != null && !secretAccessKey.isBlank();
        if (hasAccessKey != hasSecret) {
            throw new IllegalArgumentException("access-key-id and secret-access-key must be set together");
        }
        uploadUrlTtl = requireUrlTtl(uploadUrlTtl, "upload-url-ttl");
        downloadUrlTtl = requireUrlTtl(downloadUrlTtl, "download-url-ttl");
        apiCallTimeout = apiCallTimeout == null ? Duration.ofSeconds(5) : apiCallTimeout;
        apiCallAttemptTimeout = apiCallAttemptTimeout == null ? Duration.ofSeconds(3) : apiCallAttemptTimeout;
        if (apiCallAttemptTimeout.isNegative()
                || apiCallAttemptTimeout.isZero()
                || apiCallAttemptTimeout.compareTo(apiCallTimeout) > 0) {
            throw new IllegalArgumentException(
                    "api-call-attempt-timeout must be positive and at most api-call-timeout");
        }
        copyTimeout = copyTimeout == null ? DEFAULT_COPY_TIMEOUT : copyTimeout;
        if (copyTimeout.compareTo(apiCallTimeout) < 0) {
            throw new IllegalArgumentException("copy-timeout must be at least api-call-timeout");
        }
    }

    boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank();
    }

    private static Duration requireUrlTtl(Duration ttl, String name) {
        Duration value = ttl == null ? DEFAULT_URL_TTL : ttl;
        if (value.compareTo(MIN_URL_TTL) < 0 || value.compareTo(MAX_URL_TTL) > 0) {
            throw new IllegalArgumentException(name + " must be between " + MIN_URL_TTL + " and " + MAX_URL_TTL);
        }
        return value;
    }
}
