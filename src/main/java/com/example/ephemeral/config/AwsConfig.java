package com.example.ephemeral.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class AwsConfig {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(AppProperties props) {
        AppProperties.Aws aws = props.aws();
        if (StringUtils.hasText(aws.accessKey()) && StringUtils.hasText(aws.secretKey())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(aws.accessKey(), aws.secretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public S3Client s3Client(AppProperties props, AwsCredentialsProvider credentialsProvider) {
        AppProperties.Aws aws = props.aws();
        var builder = S3Client.builder()
                .region(Region.of(aws.region()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(aws.pathStyleAccess())
                        .build());
        if (StringUtils.hasText(aws.endpointOverride())) {
            builder.endpointOverride(URI.create(aws.endpointOverride()));
        }
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(AppProperties props, AwsCredentialsProvider credentialsProvider) {
        AppProperties.Aws aws = props.aws();
        var builder = S3Presigner.builder()
                .region(Region.of(aws.region()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(aws.pathStyleAccess())
                        .build());
        if (StringUtils.hasText(aws.endpointOverride())) {
            builder.endpointOverride(URI.create(aws.endpointOverride()));
        }
        return builder.build();
    }

    @Bean
    public CloudFrontUtilities cloudFrontUtilities() {
        return CloudFrontUtilities.create();
    }
}
