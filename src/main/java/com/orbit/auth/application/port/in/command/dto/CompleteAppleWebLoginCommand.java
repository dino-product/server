package com.orbit.auth.application.port.in.command.dto;

/**
 * Apple이 콜백으로 보낸 값과 로그인을 시작한 브라우저의 연결 값. 사용자가 취소하면 Apple은 code·id_token 대신 {@code appleError}를 보낸다.
 */
public record CompleteAppleWebLoginCommand(
        String state, String browserBinding, String idToken, String authorizationCode, String appleError) {

    @Override
    public String toString() {
        return "CompleteAppleWebLoginCommand[state=***, browserBinding=***, idToken=***, authorizationCode=***, "
                + "appleError=" + appleError + "]";
    }
}
