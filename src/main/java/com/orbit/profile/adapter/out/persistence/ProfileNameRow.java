package com.orbit.profile.adapter.out.persistence;

/** 이름 조회 전용 투영. 동의 이력 같은 연관을 읽지 않는다. */
record ProfileNameRow(Long accountId, String name) {}
