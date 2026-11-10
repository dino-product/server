package com.orbit.schedule.application.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.orbit.media.UploadSpec;

/**
 * 현장 사진 설정. 장당 용량은 기획이 정하지 않아([작업] §4·§11 JOB-003, schedule 차이 S-35) 값을 정하지 않고 설정으로 둔다. 비우면 저장소 한도({@link
 * UploadSpec#STORAGE_MAX_BYTES})만 적용한다. 잘못된 값은 기동 시점에 거부한다.
 *
 * @param maxBytes 사진 한 장의 최대 크기(바이트, {@code SCHEDULE_SITE_PHOTO_MAX_BYTES})
 */
@ConfigurationProperties(prefix = "app.schedule.site-photo")
public record SitePhotoProperties(Long maxBytes) {

    public SitePhotoProperties {
        maxBytes = maxBytes == null ? UploadSpec.STORAGE_MAX_BYTES : maxBytes;
        if (maxBytes < 1 || maxBytes > UploadSpec.STORAGE_MAX_BYTES) {
            throw new IllegalArgumentException(
                    "site photo max-bytes must be between 1 and " + UploadSpec.STORAGE_MAX_BYTES);
        }
    }
}
