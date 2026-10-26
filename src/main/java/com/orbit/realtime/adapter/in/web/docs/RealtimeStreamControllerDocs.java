package com.orbit.realtime.adapter.in.web.docs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.orbit.auth.AccountPrincipal;
import com.orbit.realtime.application.error.RealtimeErrorCode;
import com.orbit.shared.error.CommonErrorCode;
import com.orbit.shared.openapi.ApiErrorCodes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Realtime", description = "웹 실시간 갱신 API")
public interface RealtimeStreamControllerDocs {

    @Operation(
            summary = "실시간 갱신 연결",
            description = "요청자가 그 발주사의 활성 직원(총관리자 포함)이면 서버 전송 이벤트(SSE) 연결을 엽니다. 브라우저 EventSource는 "
                    + "Authorization 헤더를 보낼 수 없으므로 fetch 기반 클라이언트로 Bearer 토큰을 붙여 엽니다. 이벤트 값은 JSON입니다. "
                    + "연결되면 connected({})를 보내고, 연결이 끊기지 않도록 15초마다 주석 줄(:ping)을 보냅니다. "
                    + "연결 수명(30분, 연결마다 최대 10% 앞당김)이 지나거나 서버가 종료되면 reconnect({})를 보낸 뒤 닫으므로 잠시 뒤 다시 "
                    + "연결합니다. 같은 계정이 한 서버에 연결을 5개 넘게 열면 가장 오래된 연결에 closed({\"reason\":\"replaced\"})를 "
                    + "보낸 뒤 닫으며, 그 연결은 다시 연결하지 않습니다. 연결이 닫혔거나 다시 연결했으면 화면의 상태를 다시 조회합니다. "
                    + "서버가 연결을 더 받을 수 없거나 종료 중이면 REALTIME-002(503)와 Retry-After(초)를 돌려줍니다. 기사·비소속·비활성 "
                    + "계정이면 REALTIME-001, 발주사 식별자가 양수가 아니면 COMMON-400이며, 이 오류들은 Accept와 관계없이 JSON입니다. "
                    + "이벤트 스트림을 받지 않겠다는 요청은 본문 없이 406입니다.",
            security = @SecurityRequirement(name = "Bearer Authentication"),
            parameters =
                    @Parameter(
                            in = ParameterIn.HEADER,
                            name = "X-Orbit-Client",
                            description = "연결을 연 화면 종류. 지표 구분에만 쓰며, 없거나 모르는 값이면 브라우저 헤더로 추정합니다.",
                            schema = @Schema(allowableValues = {"desktop-web", "mobile-web", "app"})))
    @ApiResponse(
            responseCode = "200",
            description = "연결 성공. 응답 본문은 연결이 닫힐 때까지 이어지는 이벤트 스트림입니다.",
            content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string")))
    @ApiErrorCodes(
            enumClass = CommonErrorCode.class,
            includes = {"BAD_REQUEST", "UNAUTHORIZED"})
    @ApiErrorCodes(
            enumClass = RealtimeErrorCode.class,
            includes = {"NOT_OFFICE_MEMBER", "CONNECTION_UNAVAILABLE"})
    ResponseEntity<SseEmitter> open(
            @Parameter(hidden = true) AccountPrincipal requester,
            @Parameter(description = "발주사 식별자", example = "1", required = true) @Positive Long organizationId,
            @Parameter(hidden = true) HttpServletRequest request,
            @Parameter(hidden = true) HttpServletResponse response);
}
