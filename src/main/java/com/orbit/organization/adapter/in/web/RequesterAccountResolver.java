package com.orbit.organization.adapter.in.web;

/**
 * 현재 요청의 인증 주체에서 요청자 계정 ID를 얻는다. auth가 인증 계정을 다른 모듈용 공개 계약으로 제공하면(HM-296) 컨트롤러가 그 타입을
 * {@code @AuthenticationPrincipal}로 직접 받도록 바꾸고 이 인터페이스를 지운다.
 */
interface RequesterAccountResolver {

    Long resolve();
}
