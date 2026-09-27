package com.orbit.schedule.domain;

/**
 * 작업을 조작하는 요청자. 요청한 조직의 관리자({@link ManagerActor}, 조직 소속) 또는 기사({@link TechnicianActor}, 회사별 기사 관계)이며, 한 사용자가
 * 한 조직에서 동시에 활성으로 가지는 것은 둘 중 하나다. 행위별 허용 범위는 작업 상태·배정 정책의 권한 표를 따른다. 활성인 요청자만 만들도록 거르는 책임은 이 값을 만드는 쪽
 * (LoadActorPort)에 있다.
 */
public sealed interface Actor permits ManagerActor, TechnicianActor {

    OrganizationId organizationId();
}
