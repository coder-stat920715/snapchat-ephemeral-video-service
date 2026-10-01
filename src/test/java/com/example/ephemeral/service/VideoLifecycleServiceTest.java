//package com.example.ephemeral.service;
//
//import com.example.ephemeral.config.AppProperties;
//import com.example.ephemeral.domain.RetentionType;
//import com.example.ephemeral.domain.VideoMetadata;
//import com.example.ephemeral.domain.VideoStatus;
//import com.example.ephemeral.dto.CompleteRequest;
//import com.example.ephemeral.dto.CompleteResponse;
//import com.example.ephemeral.dto.PlayTokenRequest;
//import com.example.ephemeral.dto.UploadRequest;
//import com.example.ephemeral.dto.UploadRequestResponse;
//import com.example.ephemeral.event.DeletionMode;
//import com.example.ephemeral.event.VideoDeletionRequestedEvent;
//import com.example.ephemeral.exception.InvalidPlaybackTokenException;
//import com.example.ephemeral.exception.InvalidVideoStateException;
//import com.example.ephemeral.repository.RedisTokenRepository;
//import com.example.ephemeral.repository.VideoMetadataRepository;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.ArgumentCaptor;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.context.ApplicationEventPublisher;
//
//import java.time.Clock;
//import java.time.Duration;
//import java.time.Instant;
//import java.time.ZoneOffset;
//import java.util.Optional;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//import static org.junit.jupiter.api.Assertions.assertThrows;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.verify;
//import static org.mockito.Mockito.when;
//
//@ExtendWith(MockitoExtension.class)
//class VideoLifecycleServiceTest {
//
//    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
//
//    @Mock private VideoMetadataRepository repository;
//    @Mock private RedisTokenRepository tokens;
//    @Mock private S3PresignerService presigner;
//    @Mock private StorageGateway storage;
//    @Mock private ApplicationEventPublisher events;
//
//    private VideoLifecycleService service;
//
//    @BeforeEach
//    void setUp() {
//        AppProperties props = new AppProperties(
//                new AppProperties.Aws("us-east-1", "", "", "", true,
//                        new AppProperties.S3("bucket", Duration.ofMinutes(5), false),
//                        new AppProperties.CloudFront(false, "", "", "", Duration.ofSeconds(60))),
//                new AppProperties.Playback(Duration.ofSeconds(90)),
//                new AppProperties.Retention(Duration.ofMinutes(5), Duration.ofHours(24)),
//                new AppProperties.Cleanup(30000, 100, Duration.ofSeconds(25)));
//        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
//        service = new VideoLifecycleService(repository, tokens, presigner, storage, events, props, clock);
//    }
//
//    @Test
//    void requestUploadCreatesPendingVideoWithRetentionScopedKey() {
//        when(presigner.generateUploadUrl(anyString(), eq("video/mp4")))
//                .thenReturn(new S3PresignerService.PresignedUpload("https://s3/upload", NOW.plusSeconds(300)));
//        when(repository.save(any(VideoMetadata.class))).thenAnswer(inv -> inv.getArgument(0));
//
//        UploadRequestResponse response = service.requestUpload(
//                new UploadRequest("user-1", "my clip", 10, RetentionType.VIEW_ONCE_5MIN));
//
//        ArgumentCaptor<VideoMetadata> captor = ArgumentCaptor.forClass(VideoMetadata.class);
//        verify(repository).save(captor.capture());
//        VideoMetadata saved = captor.getValue();
//
//        assertEquals(VideoStatus.PENDING, saved.getStatus());
//        assertEquals(response.videoId(), saved.getId());
//        assertEquals("videos/view-once/" + saved.getId() + ".mp4", saved.getS3Key());
//        assertEquals("https://s3/upload", response.presignedUploadUrl());
//    }
//
//    @Test
//    void completeWithBadTokenIsRejected() {
//        VideoMetadata video = readyVideo(RetentionType.VIEW_ONCE_5MIN);
//        when(repository.findById(video.getId())).thenReturn(Optional.of(video));
//        when(tokens.burnIfMatches(video.getId(), "bad")).thenReturn(false);
//
//        assertThrows(InvalidPlaybackTokenException.class,
//                () -> service.complete(video.getId(), new CompleteRequest("bad")));
//    }
//
//    @Test
//    void completeViewOnceSchedulesHardDelete() {
//        VideoMetadata video = readyVideo(RetentionType.VIEW_ONCE_5MIN);
//        when(repository.findById(video.getId())).thenReturn(Optional.of(video));
//        when(tokens.burnIfMatches(video.getId(), "tok")).thenReturn(true);
//
//        CompleteResponse response = service.complete(video.getId(), new CompleteRequest("tok"));
//
//        ArgumentCaptor<VideoDeletionRequestedEvent> captor =
//                ArgumentCaptor.forClass(VideoDeletionRequestedEvent.class);
//        verify(events).publishEvent(captor.capture());
//        assertEquals(DeletionMode.HARD, captor.getValue().mode());
//        assertEquals(VideoStatus.EXPIRED, video.getStatus());
//        assertEquals("HARD_DELETE_SCHEDULED", response.action());
//    }
//
//    @Test
//    void secondViewerOfViewOnceVideoGetsConflict() {
//        VideoMetadata video = readyVideo(RetentionType.VIEW_ONCE_5MIN);
//        when(repository.findById(video.getId())).thenReturn(Optional.of(video));
//        when(tokens.issueIfAbsent(eq(video.getId()), anyString(), eq(Duration.ofSeconds(90)))).thenReturn(false);
//
//        assertThrows(InvalidVideoStateException.class,
//                () -> service.issuePlayToken(video.getId(), new PlayTokenRequest("viewer-2")));
//    }
//
//    private VideoMetadata readyVideo(RetentionType type) {
//        UUID id = UUID.randomUUID();
//        return VideoMetadata.builder()
//                .id(id)
//                .ownerId("user-1")
//                .title("clip")
//                .durationSeconds(10)
//                .s3Key(VideoLifecycleService.buildKey(type, id))
//                .retentionType(type)
//                .status(VideoStatus.READY)
//                .createdAt(NOW.minusSeconds(30))
//                .expiresAt(NOW.plusSeconds(270))
//                .build();
//    }
//}
