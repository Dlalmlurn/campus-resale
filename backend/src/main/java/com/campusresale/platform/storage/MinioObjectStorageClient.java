package com.campusresale.platform.storage;

import com.campusresale.platform.api.ApiExceptions;
import com.campusresale.platform.config.CampusResaleProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public class MinioObjectStorageClient implements ObjectStorageClient {

    private final MinioClient minioClient;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioObjectStorageClient(CampusResaleProperties properties) {
        this.bucket = properties.storage().bucket();
        this.minioClient = MinioClient.builder()
                .endpoint(properties.storage().endpoint())
                .credentials(properties.storage().accessKey(), properties.storage().secretKey())
                .build();
    }

    @Override
    public void putObject(String storageKey, byte[] bytes, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .contentType(contentType)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build());
        } catch (Exception exception) {
            throw ApiExceptions.internalError();
        }
    }

    @Override
    public StoredObject getObject(String storageKey) {
        try (var stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(storageKey)
                .build())) {
            return new StoredObject(stream.readAllBytes(), stream.headers().get("Content-Type"));
        } catch (Exception exception) {
            throw ApiExceptions.notFound("文件不存在或不可见");
        }
    }

    @Override
    public void deleteObject(String storageKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
        } catch (Exception ignored) {
            // Best effort cleanup. Database metadata is the source of truth for application access.
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket)
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket)
                        .build());
            }
            bucketReady.set(true);
        }
    }
}
