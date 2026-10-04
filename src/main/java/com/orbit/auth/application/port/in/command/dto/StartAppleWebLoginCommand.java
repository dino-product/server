package com.orbit.auth.application.port.in.command.dto;

/** 웹·Android Apple 로그인 시작. {@code returnUri}는 Web Adapter가 설정에 등록된 값 중에서 고른 클라이언트 복귀 주소다. */
public record StartAppleWebLoginCommand(String returnUri) {}
