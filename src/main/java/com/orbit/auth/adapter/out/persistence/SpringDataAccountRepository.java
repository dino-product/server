package com.orbit.auth.adapter.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.auth.domain.OAuthProvider;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, Long> {

    @Query("""
            select distinct a from AccountJpaEntity a
            join fetch a.credentials
            where exists (
                select 1 from OAuthCredentialJpaEntity c
                where c.account = a and c.provider = :provider and c.subject = :subject)
            """)
    Optional<AccountJpaEntity> findByCredential(
            @Param("provider") OAuthProvider provider, @Param("subject") String subject);

    @Query("select distinct a from AccountJpaEntity a join fetch a.credentials where a.id = :id")
    Optional<AccountJpaEntity> findWithCredentialsById(@Param("id") Long id);
}
