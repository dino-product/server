package com.orbit.profile.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataProfileRepository extends JpaRepository<ProfileJpaEntity, Long> {}
