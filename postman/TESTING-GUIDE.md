# Testing Guide – Snapchat Ephemeral Video Service

Collection file: `Snapchat-Ephemeral-Video-Service.postman_collection.json`
(5 folders, 36 requests, every request has automatic `pm.test` assertions and chains IDs/tokens through collection variables.)

---

## Part A – One-time setup

### A1. Start the infrastructure
```bash
cd snapchat-ephemeral-video-service
docker compose up -d
docker compose ps          # postgres, redis, localstack must be "running"/"healthy"
```

### A2. Start the application with the local profile
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```
Wait for log lines: `Created S3 bucket ephemeral-videos` (or "already exists"), `Installed S3 lifecycle backstop`, and `Started EphemeralVideoApplication`.

### A3. Import into Postman
1. Postman → **Import** → select `Snapchat-Ephemeral-Video-Service.postman_collection.json`.
2. Open the collection → **Variables** tab. Defaults are fine:

| Variable | Default | Meaning |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Spring Boot app |
| `ownerId` | `alice` | uploader |
| `viewerId` | `bob` | viewer |

All other variables (`vo_videoId`, `vo_uploadUrl`, `vo_playbackToken`, …) are filled automatically by test scripts.

### A4. Handy verification commands (keep a terminal open)
```bash
# DB rows
docker exec ephemeral-postgres psql -U ephemeral -d ephemeral -c \
 "select id, retention_type, status, expires_at from video_metadata order by created_at desc limit 5;"

# S3 objects
docker exec ephemeral-localstack awslocal s3 ls s3://ephemeral-videos --recursive

# Redis tokens and their TTL
docker exec ephemeral-redis redis-cli --scan --pattern 'video:token:*'
docker exec ephemeral-redis redis-cli TTL video:token:<videoId>
```

---

## Part B – Step-by-step manual walkthrough

Run the folders in order. Each request shows what to send, what to expect, and what to verify outside Postman.

### Folder 0 – Health
| Step | Request | Expected |
|---|---|---|
| 0.1 | `GET /actuator/health` | 200, `{"status":"UP"}` (confirms Postgres + Redis connectivity) |

### Folder 1 – VIEW_ONCE_5MIN happy path

**1.1 Upload request** – `POST /api/v1/videos/upload-request`
```json
{ "userId": "alice", "title": "1. VIEW_ONCE_5MIN happy path", "durationSeconds": 8, "retentionType": "VIEW_ONCE_5MIN" }
```
Expect **201** with `videoId`, `presignedUploadUrl` (contains `X-Amz-Signature`), `expiresAt` ≈ now + 5 min.
Verify: DB row has `status = PENDING`.

**1.2 Upload bytes to S3** – `PUT {{vo_uploadUrl}}`, header `Content-Type: video/mp4`, body = small text (or select a real mp4 under Body → binary).
Expect **200** with empty body. This request bypasses the Spring Boot app entirely (direct-to-S3 ingestion).
Verify: `s3 ls` shows `videos/view-once/<videoId>.mp4`.

**1.3 Confirm** – `POST /{videoId}/confirm`
Expect **200**, `status: READY`, `expiresAt` ≈ now + 5 min (the view-once clock starts here).
Verify: DB `status = READY`.

**1.4 Confirm again** – Expect **200** (idempotent).

**1.5 Play token** – `POST /{videoId}/play-token` `{ "userId": "bob" }`
Expect **200** with `playbackToken` and `cloudfrontSignedUrl` (locally an S3 presigned GET URL valid 60 s).
Verify: `redis-cli TTL video:token:<videoId>` returns a value ≤ 90.

**1.6 Second play token** – Expect **409** "A playback session is already active" (Redis `SET NX`).

**1.7 Play via signed URL** – `GET {{vo_signedUrl}}` → **200**, body equals the uploaded bytes. Must be run within 60 s of step 1.5.

**1.8 Complete with wrong token** – `{ "playbackToken": "not-a-real-token" }` → **410**. The real token must still exist in Redis (check with `redis-cli`).

**1.9 Complete with correct token** – `{ "playbackToken": "{{vo_playbackToken}}" }` → **200**, `action: HARD_DELETE_SCHEDULED`.
Verify (within ~1 s): DB `status = DELETED`, S3 object gone, Redis key gone. App log: `Video purged videoId=...`.

**1.10 Complete again** – **410** (token already burned; atomic Lua compare-and-delete).

**1.11 Play token after completion** – **410** "Video is no longer available".

**1.12 Old signed URL** – **404** (object deleted by the async worker).

### Folder 2 – STORE_24HR happy path
Same sequence (2.1 – 2.10) with `retentionType: STORE_24HR`. Differences to verify:
* Key is `videos/store-24hr/<videoId>.mp4`.
* Confirm returns `expiresAt` ≈ now + 24 h.
* Step 2.9 (complete) returns `SOFT_DELETE_RECORDED`; the object is **not** deleted, DB stays `READY`.
  Check the tag: `docker exec ephemeral-localstack awslocal s3api get-object-tagging --bucket ephemeral-videos --key videos/store-24hr/<videoId>.mp4` → `playback-completed=true`.
* Step 2.11: a new play token is issued successfully (200) – 24 h videos are replayable until expiry.

### Folder 3 – Negative & validation
| Req | Expected |
|---|---|
| N1 blank userId/title, duration 0 | 400 with `details[]` listing field errors |
| N2 `retentionType: FOREVER` | 400 "Malformed JSON request body or unknown enum value" |
| N3 duration 9999 | 400 |
| N4 `/not-a-uuid/confirm` | 400 |
| N5 unknown UUID | 404 |
| N6 empty body on play-token | 400 |
| N7 create a video, never upload | 201 |
| N8 confirm without S3 object | 409 "No uploaded object found" |
| N9 play-token on PENDING video | 410 |

### Folder 4 – Manual: 5-minute TTL fallback worker
1. Run F1, F2, F3 (never call `complete`).
2. Wait **≈ 6 minutes** (5 min TTL + up to 30 s sweeper interval).
3. Run F4 → **410**.
4. Verify: DB `status = DELETED`, S3 object gone, app log `Sweeper purging 1 expired videos`.

Variant – PENDING cleanup: run N7 only, wait ≈ 6.5 min (5 min URL + 1 min grace + sweeper) → row becomes `DELETED`.

---

## Part C – Automated run (Collection Runner)
1. Right-click the collection → **Run collection**.
2. Select folders **0, 1, 2, 3** (leave 4 unchecked – it needs a manual wait).
3. Set **Delay = 3000 ms** (so request 1.12 runs after the async delete) and keep persist variables on.
4. Click **Run**. Expected: all assertions pass (green).

### Newman (CI)
```bash
npm i -g newman
newman run Snapchat-Ephemeral-Video-Service.postman_collection.json \
  --folder "0. Health" --folder "1. VIEW_ONCE_5MIN happy path" \
  --folder "2. STORE_24HR happy path" --folder "3. Negative & validation" \
  --delay-request 3000
```

### Unit tests
```bash
mvn test        # VideoLifecycleServiceTest (Mockito)
```

---

## Part D – Extra scenarios worth testing by hand

| Scenario | How | Expected |
|---|---|---|
| Token expiry | Run 1.5, wait 95 s, run 1.9 | 410 (Redis TTL removed the key) |
| Signed URL expiry | Run 1.5, wait 65 s, run 1.7 | 403 `Request has expired` |
| Upload URL expiry | Run 1.1, wait 5+ min, run 1.2 | 403 from S3 |
| Content-Type mismatch | In 1.2 change header to `text/plain` | 403 SignatureDoesNotMatch |
| Concurrent burn | Fire 1.9 twice in parallel (two Postman tabs / `xargs -P2 curl`) | exactly one 200, one 410 |
| Async resilience | Stop LocalStack (`docker stop ephemeral-localstack`), run 1.9, restart LocalStack | complete returns 200 immediately; after restart the sweeper deletes the object within ~30 s |
| Lost Redis | `docker stop ephemeral-redis`, call play-token | 500 (error handled by `GlobalExceptionHandler`) |

---

## Troubleshooting
* **1.2 returns 403 SignatureDoesNotMatch** – header `Content-Type` must be exactly `video/mp4`; do not add an Authorization header.
* **1.7 returns 403 in Postman** – Postman may re-encode the query string. Use curl: `curl -L "<cloudfrontSignedUrl>" -o out.bin`.
* **Presigned URL host** – must be `localhost:4566`; make sure the app runs with `-Dspring-boot.run.profiles=local`.
* **Flyway/Hibernate validation error at startup** – run `docker compose down -v && docker compose up -d` to reset the DB.
* **Connection refused on 6379/5432/4566** – `docker compose ps`; ports must be free on the host.
* **Production (real AWS + CloudFront)** – set `CLOUDFRONT_*` variables; step 1.7 then hits `https://<distribution>/videos/...`; the rest of the flow is identical.
