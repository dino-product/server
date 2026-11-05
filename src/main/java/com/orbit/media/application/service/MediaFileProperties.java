package com.orbit.media.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.orbit.media.domain.ImageFormat;

/**
 * 파일 정책 설정. 잘못된 값은 요청마다 실패하지 않도록 기동 시점에 거부한다.
 *
 * @param imageContentTypes 업로드를 받는 이미지 형식. 시그니처로 확인할 수 있는 형식({@link ImageFormat})만 둘 수 있다
 * @param retentionMaxDays 저장소 수명 주기 규칙이 허용하는 보관 기간 상한(일, {@code MEDIA_RETENTION_MAX_DAYS}, infra와 공유)
 * @param retentionDays 용도별 보관 기간(일). 기획 보관 기간이 정해지면 채우며, 상한을 넘으면 기동에 실패한다. 지금은 비어 있다
 */
@ConfigurationProperties(prefix = "app.media")
public record MediaFileProperties(
        List<String> imageContentTypes, Integer retentionMaxDays, Map<String, Integer> retentionDays) {

    public MediaFileProperties {
        if (imageContentTypes == null || imageContentTypes.isEmpty()) {
            throw new IllegalArgumentException("app.media.image-content-types must not be empty");
        }
        Set<String> types = new LinkedHashSet<>();
        for (String type : imageContentTypes) {
            if (ImageFormat.ofContentType(type).isEmpty()) {
                throw new IllegalArgumentException("unsupported image content type: " + type);
            }
            types.add(ImageFormat.normalize(type));
        }
        imageContentTypes = List.copyOf(types);
        if (retentionMaxDays == null || retentionMaxDays < 1) {
            throw new IllegalArgumentException(
                    "app.media.retention-max-days(MEDIA_RETENTION_MAX_DAYS) must be positive");
        }
        retentionDays = retentionDays == null ? Map.of() : Map.copyOf(retentionDays);
        for (Map.Entry<String, Integer> entry : retentionDays.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 1 || entry.getValue() > retentionMaxDays) {
                throw new IllegalArgumentException("retention days of " + entry.getKey() + " must be between 1 and "
                        + retentionMaxDays + " (MEDIA_RETENTION_MAX_DAYS)");
            }
        }
    }

    boolean allows(String contentType) {
        return contentType != null && imageContentTypes.contains(ImageFormat.normalize(contentType));
    }
}
