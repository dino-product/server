package com.example.shared.internal.response;

import com.example.shared.error.BaseCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"success", "code", "message", "result"})
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) T result) {

    public static <T> ApiResponse<T> success(T result) {
        return new ApiResponse<>(true, SuccessCode.OK.getCode(), SuccessCode.OK.getMessage(), result);
    }

    public static ApiResponse<Void> failure(BaseCode errorCode) {
        return failure(errorCode, null);
    }

    public static <T> ApiResponse<T> failure(BaseCode errorCode, T result) {
        return new ApiResponse<>(false, errorCode.getCode(), errorCode.getMessage(), result);
    }
}
