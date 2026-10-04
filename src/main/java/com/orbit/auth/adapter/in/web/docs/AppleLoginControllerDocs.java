package com.orbit.auth.adapter.in.web.docs;

import com.orbit.auth.adapter.in.web.AppleLoginNonceResponse;

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
}
