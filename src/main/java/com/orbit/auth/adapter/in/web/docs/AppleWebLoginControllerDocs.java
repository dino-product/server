package com.orbit.auth.adapter.in.web.docs;

import org.springframework.http.ResponseEntity;

import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Apple web login", description = "웹·Android용 Apple 로그인(서버 authorize·callback)")
public interface AppleWebLoginControllerDocs {

    @Operation(
            summary = "Apple 웹 로그인 시작",
            description = "브라우저(Android는 Custom Tab)로 여는 주소입니다. 일회성 state·nonce를 만들고 시작한 브라우저에 연결 쿠키를 남긴 뒤 Apple 로그인 "
                    + "페이지로 302 리다이렉트합니다. 로그인이 끝나면 client에 등록된 복귀 주소로 돌아갑니다.")
    @ApiResponse(
            responseCode = "302",
            description = "Apple 로그인 페이지로 이동",
            headers = @Header(name = "Location", description = "Apple 인가 주소"))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    ResponseEntity<Void> authorize(
            @Parameter(
                            in = ParameterIn.QUERY,
                            required = true,
                            description = "복귀할 클라이언트. 설정에 등록된 이름만 허용합니다",
                            example = "web")
                    String client);
}
