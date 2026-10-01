package com.example.ephemeral.repository;

import com.example.ephemeral.domain.VideoMetadata;
import com.example.ephemeral.domain.VideoStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface VideoMetadataRepository extends JpaRepository<VideoMetadata, UUID> {

    List<VideoMetadata> findByStatusInAndExpiresAtBefore(Collection<VideoStatus> statuses,
                                                         Instant cutoff,
                                                         Pageable pageable);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update VideoMetadata v set v.status = :status, v.version = v.version + 1 where v.id = :id")
    int updateStatus(@Param("id") UUID id, @Param("status") VideoStatus status);
}
