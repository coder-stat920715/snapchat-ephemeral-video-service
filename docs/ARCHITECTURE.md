# HLD + LLD

## 1. High-level design

```
 Client ──(1) POST upload-request──▶ API ──▶ PostgreSQL (PENDING)
   │                                   └──▶ S3 presigner (PUT URL, 5 min)
   ├──(2) PUT video bytes ───────────────────────────────▶ S3 (private bucket)
   ├──(3) POST confirm ──▶ API ──HEAD object──▶ S3 ; PENDING→READY ; start TTL clock
   ├──(4) POST play-token ▶ API ──SET NX EX──▶ Redis (video:token:{id}) ; sign CloudFront URL (60 s)
   ├──(5) GET signed URL ─────────────────────────────▶ CloudFront ─▶ S3 (OAC)
   └──(6) POST complete ──▶ API ──Lua GET+DEL──▶ Redis ; publish event
                                   └─ AFTER_COMMIT ─▶ @Async worker ─▶ S3 delete + DB DELETED
```

| Concern | Mechanism |
|---|---|
| Ingestion | Presigned PUT; bytes never touch the API. Signed Content-Type `video/mp4`. |
| VIEW_ONCE_5MIN | Delete on `complete` (async) → fallback: `expires_at = confirm + 5 min`, sweeper purges every 30 s → backstop: S3 lifecycle 1 day. |
| STORE_24HR | `expires_at = confirm + 24 h` (DB TTL) → sweeper purges → S3 lifecycle (1 day) backstop. `complete` only burns the token and tags the object (soft). An SQS delay queue (visibility 24 h) can replace the sweeper at very high scale; the `purge()` method is the single place to wire it. |
| Secure delivery | CloudFront canned-policy signed URL (60 s) + single-use Redis token (TTL 90 s). |
| Atomicity | Token burn is a Lua compare-and-delete: exactly one caller can win. VIEW_ONCE issues tokens with `SET NX`, so only one viewer session exists at a time. |
| Resilience | Spring Event + `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` pool (4–16 threads, queue 500); 3 retries with backoff; rejected tasks are picked up by the sweeper; Redis lock prevents N nodes sweeping simultaneously. |
| Idempotency | S3 delete of a missing key succeeds; `confirm` on a READY video returns 200. |

## 2. Low-level design

### State machine
`PENDING → READY → EXPIRED → DELETED` (VIEW_ONCE completes via EXPIRED; STORE_24HR/PENDING go READY/PENDING → DELETED when the sweeper runs).

### Data model
`video_metadata(id uuid PK, owner_id, title, duration_seconds, s3_key unique, retention_type, status, created_at, expires_at, version)`
with index `(status, expires_at)` for the sweeper. Redis: `video:token:{videoId}` → token, explicit TTL.

### API
| Endpoint | Request | Response | Errors |
|---|---|---|---|
| `POST /api/v1/videos/upload-request` | `{userId,title,durationSeconds,retentionType}` | 201 `{videoId,presignedUploadUrl,expiresAt}` | 400 |
| `POST /api/v1/videos/{id}/confirm` | – | `{videoId,status,expiresAt}` | 404, 409 (no object / bad state), 410 (upload window expired) |
| `POST /api/v1/videos/{id}/play-token` | `{userId}` | `{playbackToken,cloudfrontSignedUrl}` | 404, 409 (session active), 410 (expired/deleted) |
| `POST /api/v1/videos/{id}/complete` | `{playbackToken}` | `{videoId,action}` | 404, 410 (token invalid/used) |

### Classes
`VideoController` → `VideoLifecycleService` → (`VideoMetadataRepository`, `RedisTokenRepository`, `S3PresignerService`, `StorageGateway`, `ApplicationEventPublisher`).
`AsyncStorageCleanupWorker` consumes `VideoDeletionRequestedEvent` and runs the `@Scheduled` sweeper.
`GlobalExceptionHandler` maps `ApiException` subclasses and validation errors to a uniform `ErrorResponse`.
