package com.prabhix.platform.files.storage;

import com.prabhix.platform.config.PrabhixProperties;
import com.prabhix.platform.files.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@Slf4j
public final class S3ObjectStore implements FileStorageService.ObjectStore {

    private final S3Client client;
    private final S3Presigner presigner;

    public S3ObjectStore(PrabhixProperties properties) {
        PrabhixProperties.Storage storage = properties.storage();
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
        var builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials);
        if (storage.endpoint() != null && !storage.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(storage.endpoint()))
                    .forcePathStyle(storage.pathStyleAccess());
        }
        this.client = builder.build();
        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials);
        if (storage.endpoint() != null && !storage.endpoint().isBlank()) {
            presignerBuilder.endpointOverride(URI.create(storage.endpoint()));
        }
        this.presigner = presignerBuilder.build();
        log.info("Object storage using S3 bucket {}", storage.bucket());
    }

    @Override
    public void put(String bucket, String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String bucket, String key) {
        return client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()).asByteArray();
    }

    @Override
    public Optional<String> signedUrl(String bucket, String key, Duration ttl) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build());
        return Optional.of(presigned.url().toString());
    }
}
