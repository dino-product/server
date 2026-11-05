package com.orbit.media.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.orbit.media.domain.UploadedFileStatus;

interface SpringDataUploadedFileRepository extends JpaRepository<UploadedFileJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from UploadedFileJpaEntity f where f.id in :ids or (f.organizationId = :organizationId"
            + " and f.purpose = :purpose and f.ownerRef = :owner and f.status = :status) order by f.id")
    List<UploadedFileJpaEntity> lockByIdsOrOwner(
            @Param("ids") Collection<UUID> ids,
            @Param("organizationId") Long organizationId,
            @Param("purpose") String purpose,
            @Param("owner") String owner,
            @Param("status") UploadedFileStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from UploadedFileJpaEntity f where f.organizationId = :organizationId"
            + " and f.purpose = :purpose and f.ownerRef = :owner and f.status = :status order by f.id")
    List<UploadedFileJpaEntity> lockByOwner(
            @Param("organizationId") Long organizationId,
            @Param("purpose") String purpose,
            @Param("owner") String owner,
            @Param("status") UploadedFileStatus status);
}
