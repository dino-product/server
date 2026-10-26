package com.orbit.auth.application.port.out;

/** 닫힌 계정의 카카오 회원번호와 이 서비스 앱의 연결을 끊는다. 이미 연결되지 않은 사용자는 끊긴 것으로 본다. */
public interface UnlinkKakaoAccountPort {

    /** @throws KakaoUnlinkException 카카오가 거절하면 REJECTED, 통신·설정·응답 오류면 UNAVAILABLE */
    void unlink(String kakaoUserId);
}
