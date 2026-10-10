package com.orbit.organization.adapter.in.web.docs;

import com.orbit.auth.AccountPrincipal;
import com.orbit.organization.adapter.in.web.MyMembershipsResponse;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Memberships", description = "소속 API")
public interface MembershipControllerDocs {

    @Operation(
            summary = "내 소속 목록 조회",
            description = "요청자의 직원 소속·기사 계약을 발주사마다 한 줄로 돌려줍니다. 활성 소속이 있으면 그 소속, 없으면 가장 최근에 비활성이 된 "
                    + "소속을 보이므로 역할 변경 전 소속은 나오지 않습니다. 비활성 소속도 상태와 함께 나옵니다. 웹 로그인 직후 발주사 선택·헤더 전환과 "
                    + "마이페이지 소속 목록에 씁니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"))
    @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MyMembershipsResponse.class)))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"UNAUTHORIZED", "NOT_FOUND"})
    MyMembershipsResponse listMine(@Parameter(hidden = true) AccountPrincipal requester);
}
