package com.orbit.profile.domain;

import java.net.URI;

/** 시행 중인 약관 문서의 버전(시행일)과 본문 주소. 버전은 동의 판정과 같은 설정값에서 나오며, 동의는 동의하는 시점의 시행 버전으로 기록된다. */
public record TermsDocument(TermsDocumentType type, String version, URI url) {

    public TermsDocument {
        if (type == null || version == null || version.isBlank() || url == null || !url.isAbsolute()) {
            throw new IllegalArgumentException("terms document needs type, version and absolute url");
        }
    }
}
