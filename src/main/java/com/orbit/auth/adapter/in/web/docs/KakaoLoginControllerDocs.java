package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.LoginNonceResponse;

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
}
