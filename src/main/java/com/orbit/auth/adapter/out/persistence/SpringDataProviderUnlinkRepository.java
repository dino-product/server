package com.orbit.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProviderUnlinkRepository extends JpaRepository<ProviderUnlinkJpaEntity, Long> {}
