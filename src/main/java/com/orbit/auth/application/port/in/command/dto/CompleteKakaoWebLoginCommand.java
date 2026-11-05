package com.orbit.auth.application.port.in.command.dto;

/**
 * 카카오가 웹 로그인 콜백으로 보낸 값과 로그인을 시작한 브라우저의 연결 값. 사용자가 취소하면 카카오는 인가 코드 대신 {@code kakaoError}(예:
 * {@code access_denied})를 보낸다.
 */
public record CompleteKakaoWebLoginCommand(
        String state, String browserBinding, String authorizationCode, String kakaoError) {

    @Override
    public String toString() {
        return "CompleteKakaoWebLoginCommand[state=***, browserBinding=***, authorizationCode=***, kakaoError="
                + kakaoError + "]";
    }
}
