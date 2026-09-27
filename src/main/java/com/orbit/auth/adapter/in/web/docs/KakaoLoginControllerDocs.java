package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.KakaoLoginRequest;
import com.orbit.auth.adapter.in.web.LoginNonceResponse;
import com.orbit.auth.adapter.in.web.LoginResponse;
import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Kakao login", description = "카카오 OIDC 로그인")
public interface KakaoLoginControllerDocs {

    @Operation(
            summary = "로그인 nonce 발급",
            description = "앱이 카카오 SDK 로그인에 넘길 일회성 nonce를 발급합니다. 5분 안에 id_token과 함께 제출해야 하며 한 번만 사용할 수 있습니다.")
    @ApiResponse(
            responseCode = "200",
            description = "발급 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginNonceResponse.class)))
    LoginNonceResponse issueNonce();

    @Operation(
            summary = "카카오 id_token으로 로그인",
            description = "카카오 SDK가 돌려준 id_token의 서명·발급자·앱 키·만료와 서버 발급 nonce를 검증하고, 처음이면 계정을 만든 뒤 Access Token을 발급합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    @ApiErrorCodes(
            enumClass = AuthErrorCode.class,
            includes = {"INVALID_ID_TOKEN", "INVALID_NONCE"})
    LoginResponse login(KakaoLoginRequest request);
}
