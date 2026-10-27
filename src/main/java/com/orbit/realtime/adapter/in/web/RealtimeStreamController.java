package com.orbit.realtime.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.orbit.auth.AccountPrincipal;
import com.orbit.realtime.adapter.in.web.docs.RealtimeStreamControllerDocs;
import com.orbit.realtime.application.error.RealtimeErrorCode;
import com.orbit.realtime.application.port.in.command.OpenRealtimeStreamUseCase;
import com.orbit.realtime.application.port.in.command.dto.OpenRealtimeStreamCommand;
import com.orbit.shared.error.BusinessException;

@Validated
@RestController
class RealtimeStreamController implements RealtimeStreamControllerDocs {

    static final String PATH = "/api/v1/organizations/{organizationId}/events";
    static final String PATH_PATTERN = "/api/v1/organizations/*/events";

    private final OpenRealtimeStreamUseCase openRealtimeStreamUseCase;
    private final RealtimeStreamProperties properties;

    RealtimeStreamController(OpenRealtimeStreamUseCase openRealtimeStreamUseCase, RealtimeStreamProperties properties) {
        this.openRealtimeStreamUseCase = openRealtimeStreamUseCase;
        this.properties = properties;
    }

    @Override
    @GetMapping(path = PATH, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> open(
            @AuthenticationPrincipal AccountPrincipal requester,
            @PathVariable Long organizationId,
            HttpServletRequest request,
            HttpServletResponse response) {
        SseRealtimeChannel channel = new SseRealtimeChannel(properties.emitterTimeout());
        OpenRealtimeStreamCommand command = new OpenRealtimeStreamCommand(
                requester.accountId(),
                organizationId,
                ClientTypeResolver.resolve(
                        request.getHeader(ClientTypeResolver.CLIENT_HEADER),
                        request.getHeader(ClientTypeResolver.MOBILE_HINT_HEADER),
                        request.getHeader(HttpHeaders.USER_AGENT)),
                channel);
        try {
            openRealtimeStreamUseCase.open(command);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == RealtimeErrorCode.CONNECTION_UNAVAILABLE) {
                // 전역 처리기는 Content-Type만 다시 정하므로 미리 넣은 헤더가 오류 응답에 남는다.
                response.setHeader(
                        HttpHeaders.RETRY_AFTER,
                        Long.toString(properties.retryAfter().toSeconds()));
            }
            throw exception;
        }
        // 앞단 프록시가 이벤트를 모아 두지 않고 바로 흘려보내게 한다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("X-Accel-Buffering", "no")
                .body(channel.emitter());
    }
}
