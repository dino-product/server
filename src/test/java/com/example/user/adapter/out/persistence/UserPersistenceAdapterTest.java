package com.example.user.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.support.TestcontainersConfiguration;
import com.example.user.domain.User;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class UserPersistenceAdapterTest {

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
