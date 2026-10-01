package com.example.ephemeral.service;

import com.example.ephemeral.config.AppProperties;
import com.example.ephemeral.config.AsyncConfig;
import com.example.ephemeral.domain.VideoMetadata;
import com.example.ephemeral.domain.VideoStatus;
import com.example.ephemeral.event.DeletionMode;
import com.example.ephemeral.event.VideoDeletionRequestedEvent;
import com.example.ephemeral.repository.RedisTokenRepository;
import com.example.ephemeral.repository.VideoMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Offloads S3 deletes from HTTP threads (event listener) and acts as the safety net
 * (scheduled sweeper) for lost events, S3 outages and the 5 min / 24 h TTL fallbacks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncStorageCleanupWorker {

    private static final int MAX_ATTEMPTS = 3;
    private static final String SWEEPER_LOCK = "storage-cleanup-sweeper";

    private final StorageGateway storage;
    private final VideoMetadataRepository repository;
    private final RedisTokenRepository tokens;
    private final AppProperties props;
    private final Clock clock;

    @Async(AsyncConfig.STORAGE_CLEANUP_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeletionRequested(VideoDeletionRequestedEvent event) {
        try {
            if (event.mode() == DeletionMode.HARD) {
                withRetry(() -> purge(event.videoId(), event.s3Key()));
            } else {
                withRetry(() -> storage.markPlaybackCompleted(event.s3Key()));
            }
        } catch (Exception e) {
            // Row stays EXPIRED/READY, so the sweeper below will retry later.
            log.error("Async cleanup failed videoId={} mode={}", event.videoId(), event.mode(), e);
        }
    }

    @Scheduled(fixedDelayString = "${app.cleanup.sweep-interval-ms:30000}")
    public void sweepExpired() {
        if (!tokens.tryAcquireLock(SWEEPER_LOCK, props.cleanup().lockTtl())) {
            return;
        }
        Instant now = clock.instant();
        List<VideoMetadata> expired = repository.findByStatusInAndExpiresAtBefore(
                EnumSet.of(VideoStatus.PENDING, VideoStatus.READY, VideoStatus.EXPIRED),
                now,
                PageRequest.of(0, props.cleanup().batchSize()));

        if (expired.isEmpty()) {
            return;
        }
        log.info("Sweeper purging {} expired videos", expired.size());
        for (VideoMetadata video : expired) {
            try {
                purge(video.getId(), video.getS3Key());
            } catch (Exception e) {
                log.error("Sweeper failed to purge videoId={}", video.getId(), e);
            }
        }
    }

    private void purge(UUID videoId, String s3Key) {
        storage.delete(s3Key);
        repository.updateStatus(videoId, VideoStatus.DELETED);
        tokens.delete(videoId);
        log.info("Video purged videoId={}", videoId);
    }

    private void withRetry(Runnable action) throws InterruptedException {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException e) {
                last = e;
                log.warn("Cleanup attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, e.getMessage());
                Thread.sleep(200L * attempt);
            }
        }
        throw last;
    }
}
