package com.orbit.schedule.adapter.out.storage;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.orbit.schedule.application.port.out.PhotoUrlPort;

/**
 * 임시 PhotoUrlPort 구현. 실제 파일 저장소가 없어 식별자를 그대로 붙인 가짜 주소({@code fake-photo://식별자})를 돌려준다. 업로드 방식이 정해지면(HM-238) 실제
 * 저장소 어댑터로 교체한 뒤 삭제한다. {@code local}·{@code test} 프로필에서만 등록한다.
 */
@Component
@Profile({"local & !prod", "test & !prod"})
class FakePhotoUrlAdapter implements PhotoUrlPort {

    @Override
    public String urlOf(String photoId) {
        return "fake-photo://" + photoId;
    }
}
