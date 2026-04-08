package cn.cctstudio.nexacord.service.impl;

import cn.cctstudio.nexacord.service.FileStorageService;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.HttpMethod;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3FileStorageServiceImpl implements FileStorageService {
    @Value("${rustfs.endpoint}")
    private String endpoint;

    @Value("${rustfs.access-key}")
    private String accessKey;

    @Value("${rustfs.secret-key}")
    private String secretKey;

    @Value("${rustfs.bucket}")
    private String bucketName;

    @Value("${rustfs.region}")
    private String region;

    @Value("${rustfs.path-style-access-enabled}")
    private boolean pathStyleAccessEnabled;

    private AmazonS3 s3Client;
    private boolean storageReady;

    @PostConstruct
    public void init() {
        try {
            BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

            ClientConfiguration clientConfig = new ClientConfiguration();
            clientConfig.setProtocol(Protocol.HTTP);

            AwsClientBuilder.EndpointConfiguration endpointConfig =
                    new AwsClientBuilder.EndpointConfiguration(endpoint, region);

            s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .withEndpointConfiguration(endpointConfig)
                    .withClientConfiguration(clientConfig)
                    .withPathStyleAccessEnabled(pathStyleAccessEnabled)
                    .build();

            if (!s3Client.doesBucketExistV2(bucketName)) {
                s3Client.createBucket(bucketName);
            }

            storageReady = true;
            log.info("RustFS/S3 file storage is ready: {}", endpoint);
        } catch (Exception ex) {
            storageReady = false;
            s3Client = null;
            log.warn(
                    "RustFS/S3 file storage is unavailable at {}. The server will continue to run, but file upload features are disabled. Cause: {}",
                    endpoint,
                    ex.getMessage()
            );
        }
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        ensureStorageReady();

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null) {
            int extensionIndex = originalFilename.lastIndexOf(".");
            if (extensionIndex >= 0) {
                extension = originalFilename.substring(extensionIndex);
            }
        }
        String fileName = UUID.randomUUID() + extension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(file.getContentType());
        metadata.setContentLength(file.getSize());

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes())) {
            s3Client.putObject(new PutObjectRequest(bucketName, fileName, inputStream, metadata));
        }

        return s3Client.getUrl(bucketName, fileName).toString();
    }

    @Override
    public List<String> uploadMultipleFiles(List<MultipartFile> files) throws IOException {
        ensureStorageReady();

        List<String> uploadedUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            uploadedUrls.add(uploadFile(file));
        }
        return uploadedUrls;
    }

    @Override
    public void deleteFile(String fileUrl) {
        ensureStorageReady();

        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            s3Client.deleteObject(new DeleteObjectRequest(bucketName, fileName));
        } catch (Exception e) {
            log.error("Failed to delete file: {}", fileUrl, e);
        }
    }

    @Override
    public String generatePresignedUrl(String fileUrl, long expirationMinutes) {
        ensureStorageReady();

        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

            Date expiration = new Date();
            long expTimeMillis = Instant.now().toEpochMilli() + expirationMinutes * 60 * 1000;
            expiration.setTime(expTimeMillis);

            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, fileName)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(expiration);

            return s3Client.generatePresignedUrl(request).toString();
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", fileUrl, e);
            return fileUrl;
        }
    }

    private void ensureStorageReady() {
        if (!storageReady || s3Client == null) {
            throw new IllegalStateException("File storage service is unavailable. Check the RustFS/S3 configuration.");
        }
    }
}
