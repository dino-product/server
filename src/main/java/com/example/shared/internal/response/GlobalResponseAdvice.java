package com.example.shared.internal.response;

import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> responseType = returnType.getParameterType();
        if (ApiResponse.class.isAssignableFrom(responseType)
                || ResponseEntity.class.isAssignableFrom(responseType)
                || Resource.class.isAssignableFrom(responseType)
                || StreamingResponseBody.class.isAssignableFrom(responseType)
                || byte[].class.isAssignableFrom(responseType)
                || String.class.isAssignableFrom(responseType)) {
            return false;
        }

        String controllerName = returnType.getContainingClass().getName();
        return !controllerName.startsWith("org.springdoc")
                && !controllerName.startsWith("org.springframework.boot.actuate")
                && !controllerName.startsWith("org.springframework.boot.autoconfigure.web");
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        return body instanceof ApiResponse<?> ? body : ApiResponse.success(body);
    }
}
