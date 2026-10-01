package com.example.ephemeral.service;

import com.example.ephemeral.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

/** Thin wrapper over S3Client so business code never touches the SDK directly. */
@Component
@RequiredArgsConstructor
public class StorageGateway {

    private final S3Client s3Client;
    private final AppProperties props;

    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket()).key(key).build());
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** Idempotent: deleting a missing key succeeds in S3. */
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build());
    }

    /** Soft delete marker used for STORE_24HR: object stays until its 24h expiry. */
    public void markPlaybackCompleted(String key) {
        s3Client.putObjectTagging(PutObjectTaggingRequest.builder()
                .bucket(bucket())
                .key(key)
                .tagging(Tagging.builder()
                        .tagSet(Tag.builder().key("playback-completed").value("true").build())
                        .build())
                .build());
    }

    private String bucket() {
        return props.aws().s3().bucket();
    }
}
