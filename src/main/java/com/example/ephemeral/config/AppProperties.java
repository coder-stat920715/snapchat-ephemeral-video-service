package com.example.ephemeral.config;

import com.example.ephemeral.domain.RetentionType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Aws aws, Playback playback, Retention retention, Cleanup cleanup) {

    public record Aws(String region,
                      String endpointOverride,
                      String accessKey,
                      String secretKey,
                      boolean pathStyleAccess,
                      S3 s3,
                      CloudFront cloudfront) {
    }

    public record S3(String bucket, Duration uploadUrlTtl, boolean autoInit) {
    }

    public record CloudFront(boolean enabled,
                             String domain,
                             String keyPairId,
                             String privateKeyPath,
                             Duration signedUrlTtl) {
    }

    public record Playback(Duration tokenTtl) {
    }

    public record Retention(Duration viewOnceTtl, Duration store24hrTtl) {
    }

    public record Cleanup(long sweepIntervalMs, int batchSize, Duration lockTtl) {
    }

    public Duration retentionFor(RetentionType type) {
        return switch (type) {
            case VIEW_ONCE_5MIN -> retention.viewOnceTtl();
            case STORE_24HR -> retention.store24hrTtl();
        };
    }
}
