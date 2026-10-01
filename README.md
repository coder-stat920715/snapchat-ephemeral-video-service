# Snapchat-style Ephemeral Video Service

Java 21 / Spring Boot 3.2 / AWS SDK v2 / Redis / PostgreSQL. See `docs/ARCHITECTURE.md` for HLD + LLD.

## Run locally (LocalStack)

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The `local` profile auto-creates the S3 bucket + lifecycle rule and returns S3 presigned GET URLs
instead of CloudFront URLs (LocalStack community has no CloudFront).

## Walk-through

```bash
# 1. ask for an upload slot
curl -s -X POST localhost:8080/api/v1/videos/upload-request \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","title":"hello","durationSeconds":8,"retentionType":"VIEW_ONCE_5MIN"}'

# 2. upload straight to S3 (Content-Type MUST be video/mp4, it is part of the signature)
curl -X PUT -H 'Content-Type: video/mp4' --data-binary @clip.mp4 "<presignedUploadUrl>"

# 3. confirm
curl -s -X POST localhost:8080/api/v1/videos/<videoId>/confirm

# 4. get a one-time playback token + signed URL
curl -s -X POST localhost:8080/api/v1/videos/<videoId>/play-token \
  -H 'Content-Type: application/json' -d '{"userId":"bob"}'

# 5. after playback ends, burn the token (VIEW_ONCE => object deleted asynchronously)
curl -s -X POST localhost:8080/api/v1/videos/<videoId>/complete \
  -H 'Content-Type: application/json' -d '{"playbackToken":"<playbackToken>"}'
```

## Production checklist

* CloudFront distribution with the bucket as origin (OAC, bucket NOT public) and a **trusted key group**;
  set `CLOUDFRONT_DOMAIN`, `CLOUDFRONT_KEY_PAIR_ID`, `CLOUDFRONT_PRIVATE_KEY_PATH` (PKCS#8 PEM).
* IAM role for the service: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`, `s3:PutObjectTagging` on `videos/*`.
* Manage the bucket + lifecycle rule via IaC and set `S3_AUTO_INIT=false`.
* Browser clients need an S3 CORS rule allowing `PUT` with `Content-Type`.
* Use ElastiCache with TLS/auth; set `REDIS_PASSWORD`.
