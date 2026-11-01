package com.orbit.organization.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.organization.application.port.out.ParticipationRequestQueryPort;
import com.orbit.organization.domain.ParticipationRequestId;

/** 거절 여부·사유 유무·없는 요청의 판정은 저장소가 하므로 {@code ParticipationRequestQueryAdapterTest}가 검증한다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("참여 요청 공개 조회")
class ParticipationRequestLookupServiceTest {

    @Mock
    private ParticipationRequestQueryPort queryPort;

    private ParticipationRequestLookupService service;

    @BeforeEach
    void setUp() {
        service = new ParticipationRequestLookupService(queryPort);
    }

    @Test
    @DisplayName("거절 사유가 있는 거절된 요청이면 그 사유를 돌려준다")
    void returnsRejectReason() {
        when(queryPort.findRejectReason(new ParticipationRequestId(31L))).thenReturn(Optional.of("인원 마감"));

        assertThat(service.findRejectReason(31L)).contains("인원 마감");
    }

    @Test
    @DisplayName("사유를 찾지 못하면(사유 없는 거절·거절되지 않음·없는 요청) 빈 값이다")
    void isEmptyWithoutRejectReason() {
        when(queryPort.findRejectReason(new ParticipationRequestId(32L))).thenReturn(Optional.empty());

        assertThat(service.findRejectReason(32L)).isEmpty();
    }

    @Test
    @DisplayName("요청 식별자가 null이거나 양수가 아니면 호출자 오류다")
    void rejectsInvalidRequestId() {
        assertThatThrownBy(() -> service.findRejectReason(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findRejectReason(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findRejectReason(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
