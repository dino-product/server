package com.orbit.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.orbit.support.PersistenceAdapterTestSupport;
import com.orbit.user.domain.User;

class UserPersistenceAdapterTest extends PersistenceAdapterTestSupport {

    @Autowired
    private SpringDataUserRepository springDataRepository;

    @Test
    void savesAndRestoresDomainUser() {
        UserPersistenceAdapter adapter = new UserPersistenceAdapter(springDataRepository);

        User saved = adapter.save(User.register("홍길동"));

        assertThat(saved.id()).isPresent();
        assertThat(adapter.findById(saved.id().orElseThrow()))
                .get()
                .extracting(User::displayName)
                .isEqualTo("홍길동");
    }
}
