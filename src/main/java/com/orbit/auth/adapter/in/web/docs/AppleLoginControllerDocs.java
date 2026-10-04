package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.AppleLoginNonceResponse;
import com.orbit.auth.adapter.in.web.AppleLoginRequest;
import com.orbit.auth.adapter.in.web.LoginResponse;
import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Apple login", description = "Apple OIDC 로그인(Sign in with Apple)")
public interface AppleLoginControllerDocs {

    @Operation(
            summary = "Apple 로그인 nonce 발급",
            description = "앱이 Apple 로그인 직전에 받을 일회성 raw nonce를 발급합니다. 앱은 그 SHA-256 hex를 Apple 요청의 nonce로 넣고, 5분 안에 "
                    + "id_token과 함께 제출해야 하며 한 번만 사용할 수 있습니다. raw 값을 그대로 넣은 id_token은 거부됩니다.")
    @ApiResponse(
            responseCode = "200",
            description = "발급 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AppleLoginNonceResponse.class)))
    AppleLoginNonceResponse issueNonce();

    @Operation(
            summary = "Apple id_token으로 로그인",
            description = "iOS 앱이 받은 Apple id_token의 서명·발급자·클라이언트·만료와 nonce 클레임(서버 발급 raw nonce의 해시)을 검증하고, "
                    + "authorization code를 교환해 같은 사용자인지 확인합니다. 처음이면 계정을 만들고 탈퇴 시 철회에 쓸 refresh token을 "
                    + "보관한 뒤 Access Token을 발급합니다. 카카오 계정과는 별개 계정입니다.")
    @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    @ApiErrorCodes(
            enumClass = AuthErrorCode.class,
            includes = {"INVALID_ID_TOKEN", "INVALID_NONCE", "INVALID_APPLE_AUTHORIZATION_CODE", "APPLE_UNAVAILABLE"})
    LoginResponse login(AppleLoginRequest request);
}
