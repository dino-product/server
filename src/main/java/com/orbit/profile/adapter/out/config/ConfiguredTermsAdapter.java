package com.orbit.profile.adapter.out.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.orbit.profile.application.port.out.CurrentTermsPort;
import com.orbit.profile.domain.TermsDocument;
import com.orbit.profile.domain.TermsDocumentType;
import com.orbit.profile.domain.TermsType;
import com.orbit.profile.domain.TermsVersions;

/**
 * 약관 버전과 본문 주소는 설정값으로 관리한다. 값이 비거나 버전이 시행일 형식이 아니거나 주소가 절대 주소가 아니면 기동에 실패한다. 본문 txt는 서버가 아니라 운영자가 웹 버킷 {@code terms/}에
 * 올린다.
 */
@Component
class ConfiguredTermsAdapter implements CurrentTermsPort {

    private static final Pattern EFFECTIVE_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final TermsVersions versions;
    private final List<TermsDocument> documents;

    ConfiguredTermsAdapter(TermsVersionProperties properties, TermsLocationProperties location) {
        this.versions = new TermsVersions(properties.service(), properties.privacy(), properties.marketing());
        String baseUrl = requireBaseUrl(location.baseUrl());
        this.documents = Arrays.stream(TermsDocumentType.values())
                .map(type -> document(type, versionOf(type, properties.privacyPolicy()), baseUrl))
                .toList();
    }

    @Override
    public TermsVersions currentVersions() {
        return versions;
    }

    @Override
    public List<TermsDocument> currentDocuments() {
        return documents;
    }

    /** 동의받는 세 종류는 동의 판정과 같은 {@link TermsVersions}에서 꺼내 값의 원본을 하나로 둔다. */
    private String versionOf(TermsDocumentType type, String privacyPolicyVersion) {
        return switch (type) {
            case SERVICE -> versions.versionOf(TermsType.SERVICE);
            case PRIVACY -> versions.versionOf(TermsType.PRIVACY);
            case MARKETING -> versions.versionOf(TermsType.MARKETING);
            case PRIVACY_POLICY -> privacyPolicyVersion;
        };
    }

    /** 버킷 경로의 종류 이름. 바꾸면 이미 올린 본문 주소가 깨진다. */
    private static String pathOf(TermsDocumentType type) {
        return switch (type) {
            case SERVICE -> "service";
            case PRIVACY -> "privacy-consent";
            case MARKETING -> "marketing";
            case PRIVACY_POLICY -> "privacy-policy";
        };
    }

    /** 버전은 시행일(YYYY-MM-DD)이다. 주소 경로에 그대로 들어가므로 다른 형식이면 기동하지 않는다. */
    private static TermsDocument document(TermsDocumentType type, String version, String baseUrl) {
        if (version == null || !EFFECTIVE_DATE.matcher(version).matches()) {
            throw new IllegalArgumentException("terms version must be an effective date (YYYY-MM-DD): " + type);
        }
        return new TermsDocument(type, version, URI.create(baseUrl + "/" + pathOf(type) + "/" + version + ".txt"));
    }

    private static String requireBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("terms base url must not be blank");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
