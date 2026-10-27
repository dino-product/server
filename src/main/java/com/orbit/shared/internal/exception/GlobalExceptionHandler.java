package com.orbit.shared.internal.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.orbit.shared.error.BaseCode;
import com.orbit.shared.error.BusinessException;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.internal.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * 오류를 공통 실패 응답으로 바꾼다. 본문이 있는 오류 응답은 요청의 Accept와 관계없이 JSON이다. 이벤트 스트림({@code text/event-stream})만 받겠다는 요청도 거부 사유를
 * 읽을 수 있게 하기 위해서다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return errorResponse(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> handleValidation(
            MethodArgumentNotValidException exception) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError -> errors.computeIfAbsent(
                        fieldError.getField(), ignored -> new java.util.ArrayList<>())
                .add(fieldError.getDefaultMessage() == null ? "올바르지 않은 값입니다." : fieldError.getDefaultMessage()));

        return errorResponse(CommonErrorCode.BAD_REQUEST, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleConstraintViolation(ConstraintViolationException exception) {
        List<String> errors = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .toList();
        return errorResponse(CommonErrorCode.BAD_REQUEST, errors);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        log.debug("Bad request", exception);
        return errorResponse(CommonErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
        log.debug("Resource not found", exception);
        return errorResponse(CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        log.debug("Method not allowed", exception);
        return errorResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 요청이 받겠다는 형식으로 응답을 만들 수 없다. 요청이 받을 수 없는 본문을 쓰지 않도록 본문 없이 406만 돌려준다(공통 처리기가 JSON을 쓰면 500이 된다).
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        log.debug("Not acceptable", exception);
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    /** 화면이 연결을 끊어(실시간 연결 종료 등) 응답을 더 쓸 수 없다. 쓸 곳이 없으므로 응답하지 않고 오류로 기록하지도 않는다. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleDisconnectedClient(AsyncRequestNotUsableException exception) {
        log.debug("Client disconnected", exception);
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnhandledException(Exception exception) {
        log.error("Unhandled exception", exception);
        return errorResponse(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(BaseCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode));
    }

    private <T> ResponseEntity<ApiResponse<T>> errorResponse(BaseCode errorCode, T result) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.failure(errorCode, result));
    }
}
