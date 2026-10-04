package com.orbit.shared.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.orbit.shared.error.BaseCode;
import com.orbit.shared.internal.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** 필터 단계에서 전역 예외 처리기와 같은 실패 응답 구조를 기록한다. */
@Component
@RequiredArgsConstructor
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, BaseCode errorCode) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(errorCode));
    }
}
