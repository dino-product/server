package com.orbit.organization.application.port.out;

/**
 * 같은 발주사·같은 종류에 삭제되지 않은 같은 이름의 유형이 이미 있어 저장소가 저장을 거부했을 때 던진다. 정상 경로는 발주사 잠금 뒤 중복을 먼저 확인하므로, 이 예외는
 * 잠금을 거치지 않은 경합에서만 나온다.
 */
public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(Throwable cause) {
        super("Undeleted category with the same name already exists", cause);
    }
}
