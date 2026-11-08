package com.orbit.profile.adapter.in.web;

import java.util.List;

import com.orbit.profile.application.port.in.query.dto.TermsDocumentInfo;
import com.orbit.profile.domain.TermsDocumentType;

import io.swagger.v3.oas.annotations.media.Schema;

public record CurrentTermsResponse(
        @Schema(description = "약관 문서마다 한 줄. SERVICE, PRIVACY, MARKETING, PRIVACY_POLICY 순서")
        List<Terms> terms) {

    static CurrentTermsResponse from(List<TermsDocumentInfo> documents) {
        return new CurrentTermsResponse(documents.stream().map(Terms::from).toList());
    }

    public record Terms(
            @Schema(
                    description = "약관 종류. SERVICE 서비스 이용약관(필수 동의), PRIVACY 개인정보 수집·이용 동의(필수 동의), MARKETING 마케팅 정보 "
                            + "수신 동의(선택 동의), PRIVACY_POLICY 개인정보 처리방침(동의 대상 아님)",
                    example = "SERVICE")
            TermsDocumentType type,

            @Schema(
                    description = "시행 중인 버전(시행일). 동의받는 약관은 동의하는 시점의 시행 버전으로 기록된다(PRIVACY_POLICY는 동의 대상 아님)",
                    example = "2026-10-10")
            String version,

            @Schema(
                    description = "본문 주소. UTF-8 텍스트(text/plain)",
                    example = "https://dev.orbi-t.com/terms/service/2026-10-10.txt")
            String url) {

        static Terms from(TermsDocumentInfo document) {
            return new Terms(document.type(), document.version(), document.url().toString());
        }
    }
}
