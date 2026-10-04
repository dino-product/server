package com.orbit.auth.application.port.in.command.dto;

import java.net.URI;
import java.time.Duration;

/**
 * Apple 웹 로그인 시작 결과. 브라우저를 {@code authorizationUri}로 보내고, {@code browserBinding}을 같은 브라우저에만 남겨 콜백이 로그인을 시작한
 * 브라우저에서 왔는지 확인한다.
 */
public record AppleWebLoginStartInfo(URI authorizationUri, String browserBinding, Duration bindingLifetime) {}
