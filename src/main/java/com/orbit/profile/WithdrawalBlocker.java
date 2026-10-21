package com.orbit.profile;

import java.util.List;

/**
 * 계정 탈퇴를 막는 조건을 판정하는 요구 인터페이스. 막아야 할 상태를 소유한 모듈이 구현하고, profile이 탈퇴 가능 여부 확인과 탈퇴 직전에 모든 구현을 부른다.
 * 예: organization은 어느 발주사에서든 유일한 총관리자, schedule은 기사 소속의 작업중·배정됨 작업([조직·계정] 정책 §5.1). 탈퇴 직전 판정은 탈퇴 트랜잭션
 * 안에서 실행되므로 상태를 바꾸지 않는다. 판정과 커밋 사이의 경합을 막으려고 같은 트랜잭션에서 잠금은 잡을 수 있다(예: 유일한 총관리자는 발주사 행을 잠근 뒤
 * 센다). 탈퇴 가능 여부 확인도 읽기 전용이 아닌 트랜잭션에서 부르므로 두 호출 모두 잠금을 잡을 수 있다. 구현이 없으면 아무것도 막지 않는다.
 */
public interface WithdrawalBlocker {

    /** 계정의 탈퇴를 막는 사유. 막을 것이 없으면 빈 목록이다. 같은 사유를 여러 번(예: 발주사별로) 돌려줘도 되며 profile이 사유마다 건수를 합친다. */
    List<WithdrawalBlock> blocksFor(Long accountId);
}
