package com.example.shared.internal.response;

import org.springframework.http.HttpStatus;

import com.example.shared.error.BaseCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SuccessCode implements BaseCode {
    OK(HttpStatus.OK, "COMMON-200", "요청에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
