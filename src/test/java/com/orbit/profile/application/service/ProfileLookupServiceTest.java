package com.orbit.profile.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.orbit.profile.application.port.out.ProfileRepository;
import com.orbit.profile.domain.AccountId;
import com.orbit.profile.domain.PersonName;

@ExtendWith(MockitoExtension.class)
@DisplayName("계정 이름 조회 공개 계약")
class ProfileLookupServiceTest {

    @Mock
    private ProfileRepository profiles;

    @InjectMocks
    private ProfileLookupService service;

    @Test
    @DisplayName("가입을 마친 계정의 이름을 계정 ID별로 돌려준다")
    void returnsNamesOfSignedUpAccounts() {
        when(profiles.findActiveNames(Set.of(new AccountId(1L), new AccountId(2L))))
                .thenReturn(Map.of(new AccountId(1L), new PersonName("홍길동")));

        assertThat(service.findNames(Set.of(1L, 2L))).containsExactlyEntriesOf(Map.of(1L, "홍길동"));
    }

    @Test
    @DisplayName("빈 ID 목록이면 저장소를 부르지 않고 빈 결과를 돌려준다")
    void returnsEmptyForNoIds() {
        assertThat(service.findNames(Set.of())).isEmpty();
        verify(profiles, never()).findActiveNames(any());
    }
}
