package com.orbit.schedule.application.port.in.query.dto;

/**
 * 작업 유형을 쓰는 작업 수.
 *
 * @param beforeStart 시작 전(등록·수락대기·수락됨) 작업 수. 유형이 지워지면 유형 없음이 된다
 * @param started 시작한(작업중·완료·취소) 작업 수. 유형이 지워져도 그대로 둔다
 */
public record WorkTypeUsageInfo(long beforeStart, long started) {}
