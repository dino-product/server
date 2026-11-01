package com.orbit.notification;

import java.time.Instant;

/**
 * 계정이 받은 알림 하나를 읽음으로 바꿨다. 같은 계정의 다른 화면이 안 읽은 수를 맞출 수 있게, 읽음으로 바꿔 저장한 트랜잭션 안에서 발행한다. 안 읽은 수가 계정 단위라
 * 받는 계정만 담는다. 발생 시각은 읽음으로 바꾼 시각이다. 이미 읽었거나 거부한 요청에는 발행하지 않으며, 같은 알림을 동시에 읽으면 두 번 발행될 수 있다.
 */
public record NotificationRead(Long accountId, Instant occurredAt) {}
