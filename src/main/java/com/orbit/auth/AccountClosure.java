package com.orbit.auth;

/**
 * 계정 폐쇄. 탈퇴를 소유한 모듈이 탈퇴 처리와 같은 트랜잭션에서 호출한다. 닫힌 계정은 외부 식별(카카오·Apple)이 모두 끊겨 같은 외부 계정의 다음 로그인이 새
 * 계정을 만들고, 커밋 뒤에는 그 계정에 발급한 Access Token을 폐기 토큰과 같이 거부한다. 계정 식별자는 다른 모듈의 기록이 계속 참조하므로 남긴다. 제공자 쪽
 * 연결 해제(Apple 토큰 철회·카카오 연결 끊기)에 필요한 값은 해제 대기로 옮겨 두며, 해제가 실패해도 폐쇄는 되돌리지 않는다.
 */
public interface AccountClosure {

    /**
     * 계정을 닫는다. 호출자 트랜잭션에 참여하므로 호출자가 롤백하면 외부 식별과 토큰이 그대로 남는다. 이미 닫힌 계정이면 저장된 상태를 바꾸지 않고, 커밋 뒤 토큰
     * 거부만 다시 확정한다.
     *
     * @throws com.orbit.shared.error.BusinessException 계정이 없으면 AUTH-004
     */
    void close(Long accountId);
}
