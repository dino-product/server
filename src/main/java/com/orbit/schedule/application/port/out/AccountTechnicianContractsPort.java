package com.orbit.schedule.application.port.out;

import java.util.List;

/**
 * 계정이 발주사들과 맺은 기사 계약을 찾는 출력 포트(ACL). 구현은 organization 모듈의 공개 계약으로 조회한다. 비활성·역할 변경으로 끝난 계약도 활성 여부와 함께
 * 돌려준다. 탈퇴 뒤처리는 organization이 같은 탈퇴로 소속을 먼저 비활성화했을 수 있어 비활성 계약까지 봐야 하기 때문이다. 한 계정은 한 발주사와 기사 계약을 하나만
 * 맺는다(역할 변경으로 끝났다가 다시 활성이 돼도 같은 계약). 계약이 없으면 빈 목록이다. 인자는 null이 아니다. 구현은 호출 시점에 커밋된 상태를 새로 읽고 캐시를
 * 쓰지 않는다.
 */
public interface AccountTechnicianContractsPort {

    List<AccountTechnicianContract> listByAccount(Long accountId);
}
