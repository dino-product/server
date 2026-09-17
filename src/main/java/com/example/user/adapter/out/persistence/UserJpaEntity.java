package com.example.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.example.user.domain.User;
import com.example.user.domain.UserId;

@Entity
@Table(name = "app_user")
class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    protected UserJpaEntity() {}

    private UserJpaEntity(String displayName) {
        this.displayName = displayName;
    }

    static UserJpaEntity from(User user) {
        return new UserJpaEntity(user.displayName());
    }

    User toDomain() {
        return User.reconstitute(new UserId(id), displayName);
    }
}
