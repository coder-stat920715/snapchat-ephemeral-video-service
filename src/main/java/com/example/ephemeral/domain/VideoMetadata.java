package com.example.ephemeral.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "video_metadata", indexes = {
        @Index(name = "idx_video_status_expires", columnList = "status, expires_at"),
        @Index(name = "idx_video_owner", columnList = "owner_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class VideoMetadata {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "s3_key", nullable = false, unique = true, length = 512)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "retention_type", nullable = false, length = 32)
    private RetentionType retentionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private VideoStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
