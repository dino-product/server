package com.orbit.profile.adapter.in.web.docs;

import com.orbit.profile.adapter.in.web.CurrentTermsResponse;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Terms", description = "약관 API")
public interface TermsControllerDocs {

    @Operation(
            summary = "시행 중인 약관 조회",
            description = "약관 문서마다 시행 중인 버전과 본문 주소를 돌려줍니다. 본문은 주소의 UTF-8 텍스트(text/plain)를 그대로 보여 주고, 다른 형식이 오면 "
                    + "보여 주지 않습니다. Access Token 없이 부릅니다. 토큰을 붙이면 유효해야 하며 폐기·만료·위조된 토큰이면 COMMON-404입니다. 가입 "
                    + "미완료·재동의 전 계정도 부를 수 있습니다. 약관 동의는 동의하는 시점의 시행 버전으로 기록되며 보통 여기서 받은 버전과 같습니다. "
                    + "버전이 바뀌면 주소도 바뀌므로 본문을 캐시해 두었다면 버전으로 비교합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CurrentTermsResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "NOT_FOUND")
    CurrentTermsResponse listCurrentTerms();
}
