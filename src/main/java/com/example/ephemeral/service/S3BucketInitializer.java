package com.example.ephemeral.service;

import com.example.ephemeral.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLifecycleConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ExpirationStatus;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.LifecycleExpiration;
import software.amazon.awssdk.services.s3.model.LifecycleRule;
import software.amazon.awssdk.services.s3.model.LifecycleRuleFilter;
import software.amazon.awssdk.services.s3.model.PutBucketLifecycleConfigurationRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Creates the bucket (if missing) and installs the S3 Lifecycle backstop:
 * every object under videos/ is expired after 1 day (S3's minimum granularity),
 * guaranteeing the 24 h retention limit even if the application is down.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.aws.s3", name = "auto-init", havingValue = "true")
public class S3BucketInitializer implements ApplicationRunner {

    private final S3Client s3Client;
    private final AppProperties props;

    @Override
    public void run(ApplicationArguments args) {
        String bucket = props.aws().s3().bucket();
        ensureBucket(bucket);
        installLifecycle(bucket);
    }

    private void ensureBucket(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("S3 bucket {} already exists", bucket);
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw e;
            }
            CreateBucketRequest.Builder request = CreateBucketRequest.builder().bucket(bucket);
            String region = props.aws().region();
            if (!"us-east-1".equals(region)) {
                request.createBucketConfiguration(
                        CreateBucketConfiguration.builder().locationConstraint(region).build());
            }
            s3Client.createBucket(request.build());
            log.info("Created S3 bucket {}", bucket);
        }
    }

    private void installLifecycle(String bucket) {
        try {
            LifecycleRule rule = LifecycleRule.builder()
                    .id("expire-ephemeral-videos-after-1-day")
                    .status(ExpirationStatus.ENABLED)
                    .filter(LifecycleRuleFilter.builder().prefix("videos/").build())
                    .expiration(LifecycleExpiration.builder().days(1).build())
                    .build();
            s3Client.putBucketLifecycleConfiguration(PutBucketLifecycleConfigurationRequest.builder()
                    .bucket(bucket)
                    .lifecycleConfiguration(BucketLifecycleConfiguration.builder().rules(rule).build())
                    .build());
            log.info("Installed S3 lifecycle backstop on bucket {}", bucket);
        } catch (S3Exception e) {
            log.warn("Could not install lifecycle rule on {}: {}", bucket, e.getMessage());
        }
    }
}
