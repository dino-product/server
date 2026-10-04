package com.orbit.auth.adapter.in.web.docs;

import org.springframework.http.ResponseEntity;

import com.orbit.auth.adapter.in.web.AppleLoginExchangeRequest;
import com.orbit.auth.adapter.in.web.LoginResponse;
import com.orbit.auth.application.error.AuthErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Operation(
            summary = "Apple 웹 로그인 콜백",
            description = "Apple이 form_post로 호출하는 콜백입니다. state를 한 번만 소비하고 시작한 브라우저의 연결 쿠키와 대조한 뒤 id_token·nonce를 검증하고 "
                    + "code를 교환합니다. 성공하면 복귀 주소에 일회성 code를, 실패하면 error(AUTH-002·003·005·006, 취소는 AUTH-008)를 실어 302로 "
                    + "돌려보냅니다. state가 없거나 다른 브라우저에서 온 콜백은 복귀하지 않고 401로 끝냅니다. 이름·이메일(user)은 저장하지 않습니다.")
    @ApiResponse(
            responseCode = "302",
            description = "클라이언트 복귀 주소로 이동",
            headers = @Header(name = "Location", description = "등록된 복귀 주소?code=... 또는 ?error=AUTH-xxx"))
    @ApiErrorCodes(enumClass = AuthErrorCode.class, includes = "INVALID_NONCE")
    ResponseEntity<Void> callback(
            @Parameter(description = "인가 요청에 실은 state", example = "Zm9vYmFyYmF6cXV4") String state,
            @Parameter(description = "Apple authorization code") String code,
            @Parameter(name = "id_token", description = "Apple id_token") String idToken,
            @Parameter(description = "사용자가 취소하는 등 Apple이 보낸 오류", example = "user_cancelled_authorize") String error,
            @Parameter(hidden = true) String browserBinding);

    @Operation(
            summary = "Apple 웹 로그인 교환",
            description = "콜백이 복귀 주소로 넘긴 일회성 교환 코드(60초)를 Access Token으로 바꿉니다. 코드는 한 번만 쓸 수 있습니다.")
    @ApiResponse(
            responseCode = "200",
            description = "교환 성공",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class)))
    @ApiErrorCodes(enumClass = CommonErrorCode.class, includes = "BAD_REQUEST")
    @ApiErrorCodes(enumClass = AuthErrorCode.class, includes = "INVALID_APPLE_LOGIN_EXCHANGE_CODE")
    LoginResponse exchange(AppleLoginExchangeRequest request);
}
