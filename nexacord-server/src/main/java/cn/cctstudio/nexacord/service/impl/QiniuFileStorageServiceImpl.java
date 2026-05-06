package cn.cctstudio.nexacord.service.impl;

import cn.cctstudio.nexacord.service.FileStorageService;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QiniuFileStorageServiceImpl implements FileStorageService {
    private static final int MAX_EXTENSION_LENGTH = 24;
    private static final ProxySelector DIRECT_PROXY_SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            log.warn("七牛云直连失败：uri={}, address={}, reason={}", uri, sa, ioe.getMessage());
        }
    };

    @Value("${qiniu.access-key:}")
    private String accessKey;

    @Value("${qiniu.secret-key:}")
    private String secretKey;

    @Value("${qiniu.bucket:nexacord}")
    private String bucketName;

    @Value("${qiniu.domain:oss2.cctstudio.cn}")
    private String domain;

    @Value("${qiniu.use-https:true}")
    private boolean useHttps;

    @Value("${qiniu.private-bucket:false}")
    private boolean privateBucket;

    @Value("${qiniu.region:auto}")
    private String region;

    private Auth auth;
    private UploadManager uploadManager;
    private BucketManager bucketManager;
    private boolean storageReady;

    @PostConstruct
    public void init() {
        if (isBlank(accessKey) || isBlank(secretKey) || isBlank(bucketName) || isBlank(domain)) {
            storageReady = false;
            log.warn("七牛云对象存储配置不完整，文件上传功能已禁用。");
            return;
        }

        Configuration configuration = buildWithoutSystemProxy(() -> Configuration.create(resolveRegion(region)));
        configuration.useHttpsDomains = useHttps;
        auth = Auth.create(accessKey, secretKey);
        uploadManager = buildWithoutSystemProxy(() -> new UploadManager(configuration));
        bucketManager = buildWithoutSystemProxy(() -> new BucketManager(auth, configuration));
        storageReady = true;
        log.info("七牛云对象存储已就绪：bucket={}, domain={}", bucketName, domain);
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        ensureStorageReady();

        if (file.isEmpty()) {
            throw new IOException("不能上传空文件。");
        }

        String key = buildObjectKey(file);
        try {
            Response response = uploadManager.put(file.getBytes(), key, auth.uploadToken(bucketName));
            DefaultPutRet putRet = response.jsonToObject(DefaultPutRet.class);
            return buildPublicUrl(isBlank(putRet.key) ? key : putRet.key);
        } catch (QiniuException ex) {
            throw new IOException("上传文件到七牛云失败：" + readQiniuMessage(ex), ex);
        }
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
            bucketManager.delete(bucketName, extractKey(fileUrl));
        } catch (QiniuException ex) {
            log.warn("删除七牛云文件失败：{}，原因：{}", fileUrl, readQiniuMessage(ex));
        }
    }

    @Override
    public String generatePresignedUrl(String fileUrl, long expirationMinutes) {
        ensureStorageReady();

        String publicUrl = buildPublicUrl(extractKey(fileUrl));
        if (!privateBucket) {
            return publicUrl;
        }

        long expireSeconds = Math.max(1, expirationMinutes) * 60;
        return auth.privateDownloadUrl(publicUrl, expireSeconds);
    }

    private Region resolveRegion(String configuredRegion) {
        String normalizedRegion = configuredRegion == null ? "auto" : configuredRegion.trim().toLowerCase();
        return switch (normalizedRegion) {
            case "z0", "huadong", "region0" -> Region.region0();
            case "z1", "huabei", "region1" -> Region.region1();
            case "z2", "huanan", "region2" -> Region.region2();
            case "na0", "beimei" -> Region.regionNa0();
            case "as0", "xinjiapo" -> Region.regionAs0();
            default -> Region.autoRegion();
        };
    }

    private <T> T buildWithoutSystemProxy(Supplier<T> supplier) {
        ProxySelector previousSelector = ProxySelector.getDefault();
        try {
            ProxySelector.setDefault(DIRECT_PROXY_SELECTOR);
            return supplier.get();
        } finally {
            ProxySelector.setDefault(previousSelector);
        }
    }

    private String buildObjectKey(MultipartFile file) {
        return "attachments/" + UUID.randomUUID() + getSafeExtension(file.getOriginalFilename());
    }

    private String getSafeExtension(String originalFilename) {
        if (isBlank(originalFilename)) {
            return "";
        }

        String filename = originalFilename.replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == filename.length() - 1) {
            return "";
        }

        String extension = filename.substring(extensionIndex).toLowerCase();
        if (extension.length() > MAX_EXTENSION_LENGTH || !extension.matches("\\.[a-z0-9]+")) {
            return "";
        }
        return extension;
    }

    private String buildPublicUrl(String key) {
        String baseDomain = domain.trim().replaceAll("/+$", "");
        if (!baseDomain.startsWith("http://") && !baseDomain.startsWith("https://")) {
            baseDomain = (useHttps ? "https://" : "http://") + baseDomain;
        }
        return baseDomain + "/" + encodeKey(key);
    }

    private String encodeKey(String key) {
        return Arrays.stream(key.split("/"))
                .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private String extractKey(String fileUrl) {
        if (isBlank(fileUrl)) {
            throw new IllegalArgumentException("文件地址不能为空。");
        }

        try {
            URI uri = URI.create(fileUrl);
            String path = uri.getPath();
            if (!isBlank(path)) {
                String key = path.startsWith("/") ? path.substring(1) : path;
                return URLDecoder.decode(key, StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException ignored) {
            // 兼容历史数据里只保存 key 的情况。
        }

        return URLDecoder.decode(fileUrl, StandardCharsets.UTF_8);
    }

    private String readQiniuMessage(QiniuException ex) {
        if (ex.response == null) {
            return ex.getMessage();
        }
        try {
            return ex.response.toString();
        } catch (Exception ignored) {
            return ex.getMessage();
        }
    }

    private void ensureStorageReady() {
        if (!storageReady || auth == null || uploadManager == null || bucketManager == null) {
            throw new IllegalStateException("七牛云对象存储尚未配置完整，请检查对象存储环境变量。");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
