package com.orbit.schedule.application.port.out;

/**
 * 완료보고 사진 식별자를 내려받을 주소로 바꾸는 출력 포트. 사진은 업로드를 먼저 마친 파일의 식별자로 저장하며, 저장소 종류·주소 형식·만료는 업로드 방식과 함께
 * 정한다(HM-238). 식별자는 null이 아니다.
 */
public interface PhotoUrlPort {

    String urlOf(String photoId);
}
