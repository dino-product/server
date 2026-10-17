package com.orbit.schedule.application.port.out;

/**
 * 조직이 정한 분류(작업유형·기사유형·직원유형)의 표시 정보. 조회 화면의 표시와 조회 필터에만 쓰고 변경 규칙의 판정에는 쓰지 않는다.
 *
 * @param id 유형 식별자(양수)
 * @param name 유형 이름
 * @param color 유형 색(조직이 정한 값). 없으면 null
 * @param deleted 조직이 삭제한 유형인지. 작업유형만 쓴다(이미 작업에 붙어 있던 유형은 삭제된 뒤에도 표시 정보를 찾을 수 있음)
 */
public record TypeLabel(Long id, String name, String color, boolean deleted) {

    public TypeLabel {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
    }
}
