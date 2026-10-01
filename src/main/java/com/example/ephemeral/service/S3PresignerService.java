package com.example.ephemeral.service;

import com.example.ephemeral.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class S3PresignerService {

    public record PresignedUpload(String url, Instant expiresAt) {
    }

    private final S3Presigner presigner;
    private final CloudFrontUtilities cloudFrontUtilities;
    private final AppProperties props;
    private final Clock clock;

    /** Presigned PUT valid for app.aws.s3.upload-url-ttl (5 min). Client must send the same Content-Type. */
    public PresignedUpload generateUploadUrl(String s3Key, String contentType) {
        PutObjectRequest putObject = PutObjectRequest.builder()
                .bucket(props.aws().s3().bucket())
                .key(s3Key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(props.aws().s3().uploadUrlTtl())
                .putObjectRequest(putObject)
                .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return new PresignedUpload(presigned.url().toString(), presigned.expiration());
    }

    /**
     * CloudFront canned-policy signed URL valid for app.aws.cloudfront.signed-url-ttl (60 s).
     * When CloudFront is disabled (LocalStack) a short-lived S3 presigned GET URL is returned instead.
     */
    public String generatePlaybackUrl(String s3Key) {
        AppProperties.CloudFront cf = props.aws().cloudfront();
        if (!cf.enabled()) {
            return presignedGetUrl(s3Key, cf);
        }
        try {
            CannedSignerRequest signerRequest = CannedSignerRequest.builder()
                    .resourceUrl("https://" + cf.domain() + "/" + s3Key)
                    .privateKey(Path.of(cf.privateKeyPath()))
                    .keyPairId(cf.keyPairId())
                    .expirationDate(clock.instant().plus(cf.signedUrlTtl()))
                    .build();
            return cloudFrontUtilities.getSignedUrlWithCannedPolicy(signerRequest).url();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign CloudFront URL", e);
        }
    }

    private String presignedGetUrl(String s3Key, AppProperties.CloudFront cf) {
        GetObjectRequest getObject = GetObjectRequest.builder()
                .bucket(props.aws().s3().bucket())
                .key(s3Key)
                .build();
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(cf.signedUrlTtl())
                .getObjectRequest(getObject)
                .build();
        return presigner.presignGetObject(request).url().toString();
    }
}
