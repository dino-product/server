package com.orbit.organization;

import java.util.Optional;

/**
 * 참여 요청의 공개 조회. 다른 모듈이 참여 요청 이벤트의 요청 식별자로, 이벤트에 싣지 않는 값(직접 입력한 거절 사유)을 찾을 때 쓴다. 호출 트랜잭션에서 보이는 DB 상태를
 * 읽는다. 인자는 null이 아닌 양수이며 어기면 {@link IllegalArgumentException}이다.
 */
public interface ParticipationRequestLookup {

    /** 거절된 요청이고 거절 사유가 있으면 그 사유를, 사유 없이 거절됐거나 거절되지 않은 요청, 없는 요청이면 빈 값을 돌려준다. */
    Optional<String> findRejectReason(Long requestId);
}
