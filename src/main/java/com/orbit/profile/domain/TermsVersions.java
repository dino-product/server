package com.orbit.profile.domain;

/** 지금 시행 중인 약관 종류별 버전. 필수 약관의 버전이 바뀌면 이전 버전에만 동의한 계정은 다시 동의해야 한다. */
public record TermsVersions(String service, String privacy, String marketing) {

    public TermsVersions {
        if (isBlank(service) || isBlank(privacy) || isBlank(marketing)) {
            throw new IllegalArgumentException("terms versions must not be blank");
        }
    }

    public String versionOf(TermsType type) {
        return switch (type) {
            case SERVICE -> service;
            case PRIVACY -> privacy;
            case MARKETING -> marketing;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
