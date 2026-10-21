package com.orbit.auth.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /** 닫힌 계정은 외부 식별이 없으므로 외부 식별을 바깥 조인으로 읽는다. */
    @Query("select distinct a from AccountJpaEntity a left join fetch a.credentials where a.id = :id")
    Optional<AccountJpaEntity> findWithCredentialsById(@Param("id") Long id);

    /** PostgreSQL은 바깥 조인의 nullable 쪽을 잠글 수 없으므로 계정 행만 잠그고 외부 식별은 지연 로딩한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountJpaEntity a where a.id = :id")
    Optional<AccountJpaEntity> findLockedById(@Param("id") Long id);
}
