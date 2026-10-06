package com.orbit.auth.application.port.in.command.dto;

/**
 * 웹·Android Apple 로그인 시작. {@code returnUri}는 Web Adapter가 설정에 등록된 값 중에서 고른 클라이언트 복귀 주소이고, {@code codeChallenge}는
 * 클라이언트가 만든 PKCE S256 값으로 콜백 뒤 교환 코드를 그 클라이언트에 묶는다.
 */
public record StartAppleWebLoginCommand(String returnUri, String codeChallenge) {}
