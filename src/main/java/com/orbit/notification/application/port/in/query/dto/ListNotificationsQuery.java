package com.orbit.notification.application.port.in.query.dto;

/**
 * 요청 계정이 받은 알림 목록 한 쪽.
 *
 * @param unreadOnly 안 읽은 알림만
 * @param page 0부터 시작하는 쪽 번호. null이면 0이고, 건너뛰는 알림 수(page × size)는 10,000건 이하다
 * @param size 한 쪽의 알림 수(1~100). null이면 20
 */
public record ListNotificationsQuery(Long accountId, boolean unreadOnly, Integer page, Integer size) {}
