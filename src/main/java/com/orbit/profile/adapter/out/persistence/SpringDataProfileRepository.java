package com.orbit.profile.adapter.out.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.profile.domain.SignupStatus;

interface SpringDataProfileRepository extends JpaRepository<ProfileJpaEntity, Long> {

    @Query("""
            select new com.orbit.profile.adapter.out.persistence.ProfileNameRow(p.accountId, p.name)
            from ProfileJpaEntity p
            where p.accountId in :accountIds and p.status = :status
            """)
    List<ProfileNameRow> findNames(
            @Param("accountIds") Collection<Long> accountIds, @Param("status") SignupStatus status);
}
