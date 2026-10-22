package com.orbit.notification.application.port.in.command.dto;

import java.time.Duration;

import com.orbit.notification.domain.RetryBackoff;

/**
 * 발신 대기열 한 번 처리의 설정.
 *
 * @param batchSize 한 번에 집는 최대 건수
 * @param inFlightTimeout 집은 발신의 점유 시간. 이 안에 결과가 없으면(발신 중 인스턴스 중단) 다시 집는다
 * @param backoff 다시 보낼 실패의 대기 시간
 */
public record DispatchPushDeliveriesCommand(int batchSize, Duration inFlightTimeout, RetryBackoff backoff) {}
