package com.example.ephemeral.service;

import com.example.ephemeral.config.AppProperties;
import com.example.ephemeral.domain.RetentionType;
import com.example.ephemeral.domain.VideoMetadata;
import com.example.ephemeral.domain.VideoStatus;
import com.example.ephemeral.dto.CompleteRequest;
import com.example.ephemeral.dto.CompleteResponse;
import com.example.ephemeral.dto.PlayTokenRequest;
import com.example.ephemeral.dto.PlayTokenResponse;
import com.example.ephemeral.dto.UploadRequest;
import com.example.ephemeral.dto.UploadRequestResponse;
import com.example.ephemeral.dto.VideoStatusResponse;
import com.example.ephemeral.event.DeletionMode;
import com.example.ephemeral.event.VideoDeletionRequestedEvent;
import com.example.ephemeral.exception.InvalidPlaybackTokenException;
import com.example.ephemeral.exception.InvalidVideoStateException;
import com.example.ephemeral.exception.VideoNotFoundException;
import com.example.ephemeral.exception.VideoUnavailableException;
import com.example.ephemeral.repository.RedisTokenRepository;
import com.example.ephemeral.repository.VideoMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoLifecycleService {

    static final String CONTENT_TYPE = "video/mp4";
    private static final Duration PENDING_GRACE = Duration.ofMinutes(1);

    private final VideoMetadataRepository repository;
    private final RedisTokenRepository tokens;
    private final S3PresignerService presigner;
    private final StorageGateway storage;
    private final ApplicationEventPublisher events;
    private final AppProperties props;
    private final Clock clock;

    /** Step 1: reserve a videoId + S3 key (PENDING) and hand the client a presigned PUT URL. */
    @Transactional
    public UploadRequestResponse requestUpload(UploadRequest request) {
        UUID videoId = UUID.randomUUID();
        Instant now = clock.instant();
        String s3Key = buildKey(request.retentionType(), videoId);

        S3PresignerService.PresignedUpload upload = presigner.generateUploadUrl(s3Key, CONTENT_TYPE);

        VideoMetadata video = VideoMetadata.builder()
                .id(videoId)
                .ownerId(request.userId())
                .title(request.title())
                .durationSeconds(request.durationSeconds())
                .s3Key(s3Key)
                .retentionType(request.retentionType())
                .status(VideoStatus.PENDING)
                .createdAt(now)
                // A PENDING row that is never confirmed is purged shortly after the upload URL dies.
                .expiresAt(upload.expiresAt().plus(PENDING_GRACE))
                .build();
        repository.save(video);

        log.info("Upload requested videoId={} owner={} retention={}", videoId, request.userId(), request.retentionType());
        return new UploadRequestResponse(videoId, upload.url(), upload.expiresAt());
    }

    /** Step 2: verify the object really landed in S3, flip PENDING -> READY and start the retention clock. */
    @Transactional
    public VideoStatusResponse confirmUpload(UUID videoId) {
        VideoMetadata video = find(videoId);
        Instant now = clock.instant();

        if (video.getStatus() == VideoStatus.READY) {
            return toStatus(video);
        }
        if (video.getStatus() != VideoStatus.PENDING) {
            throw new InvalidVideoStateException("Video is " + video.getStatus() + " and cannot be confirmed");
        }
        if (!now.isBefore(video.getExpiresAt())) {
            throw new VideoUnavailableException("Upload window expired for video " + videoId);
        }
        if (!storage.exists(video.getS3Key())) {
            throw new InvalidVideoStateException("No uploaded object found in storage for video " + videoId);
        }

        video.setStatus(VideoStatus.READY);
        video.setExpiresAt(now.plus(props.retentionFor(video.getRetentionType())));
        repository.save(video);

        log.info("Video confirmed videoId={} expiresAt={}", videoId, video.getExpiresAt());
        return toStatus(video);
    }

    /** Step 3: issue a single-use Redis token plus a 60 s CloudFront signed URL. */
    public PlayTokenResponse issuePlayToken(UUID videoId, PlayTokenRequest request) {
        VideoMetadata video = find(videoId);
        Instant now = clock.instant();

        if (video.getStatus() != VideoStatus.READY || !now.isBefore(video.getExpiresAt())) {
            throw new VideoUnavailableException("Video is no longer available");
        }

        String token = UUID.randomUUID().toString();
        Duration ttl = props.playback().tokenTtl();

        if (video.getRetentionType() == RetentionType.VIEW_ONCE_5MIN) {
            // Exactly one live token at a time: concurrent viewers lose the SET NX race.
            if (!tokens.issueIfAbsent(videoId, token, ttl)) {
                throw new InvalidVideoStateException("A playback session is already active for this video");
            }
        } else {
            tokens.issue(videoId, token, ttl);
        }

        try {
            String signedUrl = presigner.generatePlaybackUrl(video.getS3Key());
            log.info("Playback token issued videoId={} viewer={}", videoId, request.userId());
            return new PlayTokenResponse(token, signedUrl);
        } catch (RuntimeException e) {
            tokens.delete(videoId);
            throw e;
        }
    }

    /** Step 4: burn the token atomically; VIEW_ONCE videos are hard-deleted asynchronously. */
    @Transactional
    public CompleteResponse complete(UUID videoId, CompleteRequest request) {
        VideoMetadata video = find(videoId);

        if (!tokens.burnIfMatches(videoId, request.playbackToken())) {
            throw new InvalidPlaybackTokenException();
        }

        if (video.getRetentionType() == RetentionType.VIEW_ONCE_5MIN) {
            video.setStatus(VideoStatus.EXPIRED);
            video.setExpiresAt(clock.instant());
            repository.save(video);
            // Published inside the transaction; the listener fires AFTER_COMMIT on the async pool.
            events.publishEvent(new VideoDeletionRequestedEvent(videoId, video.getS3Key(), DeletionMode.HARD));
            log.info("Playback completed, hard delete scheduled videoId={}", videoId);
            return new CompleteResponse(videoId, "HARD_DELETE_SCHEDULED");
        }

        events.publishEvent(new VideoDeletionRequestedEvent(videoId, video.getS3Key(), DeletionMode.SOFT));
        log.info("Playback completed, soft delete recorded videoId={}", videoId);
        return new CompleteResponse(videoId, "SOFT_DELETE_RECORDED");
    }

    static String buildKey(RetentionType type, UUID videoId) {
        return "videos/" + type.getKeyPrefix() + "/" + videoId + ".mp4";
    }

    private VideoMetadata find(UUID videoId) {
        return repository.findById(videoId).orElseThrow(() -> new VideoNotFoundException(videoId));
    }

    private VideoStatusResponse toStatus(VideoMetadata video) {
        return new VideoStatusResponse(video.getId(), video.getStatus(), video.getExpiresAt());
    }
}
