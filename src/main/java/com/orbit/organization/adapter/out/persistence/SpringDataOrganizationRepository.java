package com.orbit.organization.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface SpringDataOrganizationRepository extends JpaRepository<OrganizationJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrganizationJpaEntity o where o.id = :id")
    Optional<OrganizationJpaEntity> findByIdForUpdate(Long id);

    /**
     * 현재 코드와 폐기 코드를 한 문장으로 확인한다. 다른 트랜잭션의 코드 변경이 커밋되는 순간에도 그 코드는 둘 중 한쪽에서 보이므로, 폐기된 코드가 확인을 빠져나가지
     * 않는다.
     */
    @Query(
            value = "select exists(select 1 from companies where code = :code)"
                    + " or exists(select 1 from retired_company_codes where code = :code)",
            nativeQuery = true)
    boolean existsIssuedCode(String code);
}
