package com.orbit.organization.adapter.out.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.orbit.organization.domain.AccountId;
import com.orbit.profile.ProfileLookup;

@DisplayName("profile 이름 조회 ACL")
class ProfileAccountNamesAdapterTest {

    private final ProfileLookup profileLookup = mock(ProfileLookup.class);
    private final ProfileAccountNamesAdapter adapter = new ProfileAccountNamesAdapter(profileLookup);

    @Test
    @DisplayName("계정 식별자로 profile 이름을 찾아 organization 계정 식별자별 이름으로 옮기고, 이름이 없는 계정은 빼고 돌려준다")
    void translatesNamesByAccountId() {
        when(profileLookup.findNames(Set.of(7L, 8L))).thenReturn(Map.of(7L, "홍길동"));

        assertThat(adapter.findNames(Set.of(new AccountId(7L), new AccountId(8L))))
                .containsExactly(Map.entry(new AccountId(7L), "홍길동"));
    }

    @Test
    @DisplayName("찾을 계정이 없으면 profile을 부르지 않는다")
    void skipsLookupForEmptyAccounts() {
        assertThat(adapter.findNames(Set.of())).isEmpty();
        verifyNoInteractions(profileLookup);
    }
}
