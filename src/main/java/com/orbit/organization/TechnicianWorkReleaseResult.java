package com.orbit.organization;

/** 역할 변경에 따른 대기함 반환 결과. 돌렸거나({@link WorkReleased}) 작업중 작업 때문에 아무것도 바꾸지 않았다({@link WorkReleaseBlocked}). */
public sealed interface TechnicianWorkReleaseResult permits WorkReleased, WorkReleaseBlocked {}
